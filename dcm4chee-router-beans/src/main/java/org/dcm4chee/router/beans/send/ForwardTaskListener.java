/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.router.beans.send;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.ejb.EJB;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.transform.Templates;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.io.SAXWriter;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Connection;
import org.dcm4che.net.DataWriter;
import org.dcm4che.net.DataWriterAdapter;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.NoPresentationContextException;
import org.dcm4che.net.Priority;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.router.ejb.AuditLogManager;
import org.dcm4chee.router.ejb.FileCacheManager;
import org.dcm4chee.router.ejb.ForwardTaskManager;
import org.dcm4chee.router.persistence.AuditLog;
import org.dcm4chee.router.persistence.FileCache;
import org.dcm4chee.router.persistence.ForwardTask;
import org.dcm4chee.router.persistence.ForwardTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ForwardTaskListener implements MessageListener {
    
    private static final Logger LOG =
        LoggerFactory.getLogger(ForwardTaskListener.class);
    
    @EJB
    private ForwardTaskManager forwardTaskMgr;
    
    @EJB
    private FileCacheManager fileCacheMgr;
    
    @EJB
    private AuditLogManager auditLogMgr;
    
    private QueueConnectionFactory qconFactory;
    private Queue queue;
    private QueueConnection qconn;
    private QueueSession qsession;
    private QueueReceiver qreceiver;
    private Connection remote = new Connection();
    private Connection conn = new Connection();
    private AAssociateRQ rq = new AAssociateRQ();
    private Association as;
    private ApplicationEntity ae;
    private int priority;
    private List<FileCache> fileCacheList;
    private Templates templates;
    
    private long totalSize;
    private int filesSent;
    
    public void setAe(ApplicationEntity ae) {
        this.ae = ae;
    }

    public ApplicationEntity getAe() {
        return ae;
    }
    
    public final void setPriority(int priority) {
        this.priority = priority;
    }

    public void start() throws JMSException, NamingException {
        if (qconFactory == null) {
            InitialContext ctx = new InitialContext();
            try {
                qconFactory = (QueueConnectionFactory) ctx.lookup("ConnectionFactory");
                queue = (Queue) ctx.lookup("queue/ForwardTaskQueue");
            } finally {
                ctx.close();
            }
        }
        qconn = qconFactory.createQueueConnection();
        qsession = qconn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
        qreceiver = qsession.createReceiver(queue);
        qreceiver.setMessageListener(this);
        qconn.start();
    }

    public void stop() throws JMSException {
        qreceiver.close();
        qsession.close();
        qconn.close();
    }

    @Override
    public void onMessage(Message message) {
        ForwardTask ft = null;
        try {
            ft = (ForwardTask)((ObjectMessage)message).getObject();
            Map<String, Connection> forwardConnections =
                (Map<String, Connection>) ae.getDevice().getProperty("Forward.connections");
            remote = forwardConnections.get(ft.getDestinationAET());
            remote.setTlsProtocol(conn.getTlsProtocols());
            remote.setTlsCipherSuite(conn.getTlsCipherSuite());
            conn = ae.findCompatibelConnection(remote);
            rq.setCalledAET(ft.getDestinationAET());
            fileCacheList = fileCacheMgr.findByFilesetUID(ft.getFilesetUID());
            addPresentationContext();
            setPriority(Priority.NORMAL);
            open();
            sendFiles(ft);
        } catch (IncompatibleConnectionException e) {
            LOG.error(e.getMessage());
            forwardTaskMgr.remove(ft.getPk());
        } catch (Exception e) {
            LOG.error(e.getMessage());
            if (!ft.getForwardTaskStatus().equals(ForwardTaskStatus.FAILED))
                forwardTaskMgr.updateForwardTaskStatus(ForwardTaskStatus.FAILED, e.getMessage(), ft.getPk());
        } finally {
            try {
                close();
            } catch (Exception closeError) {
                LOG.error(closeError.getMessage());;
            }
        }
    }
    
    private void addPresentationContext() throws IOException {
        for (FileCache fc : fileCacheList) {
            String cuid = fc.getSopClassUID();
            String ts = fc.getTransferSyntaxUID();
            if (rq.containsPresentationContextFor(cuid, ts))
                return;

            if (!rq.containsPresentationContextFor(cuid)) {
                if (!ts.equals(UID.ImplicitVRLittleEndian))
                    rq.addPresentationContext(new PresentationContext(
                            rq.getNumberOfPresentationContexts() * 2 + 1, 
                            cuid, UID.ImplicitVRLittleEndian));
            }
            rq.addPresentationContext(new PresentationContext(
                    rq.getNumberOfPresentationContexts() * 2 + 1, cuid, ts));
        }
    }
    
    public void open() throws IOException, InterruptedException, IncompatibleConnectionException {
        if (rq.getNumberOfPresentationContexts() == 0)
            rq.addPresentationContext(new PresentationContext(1, UID.VerificationSOPClass,
                    UID.ImplicitVRLittleEndian));
        as = ae.connect(conn, remote, rq);
    }
    
    @SuppressWarnings("unchecked")
    public void sendFiles(ForwardTask ft) throws IOException {
        long t1, t2, send = 0;
        try {
            Map<String, Templates> map = (Map<String, Templates>) ae.getProperty("Retrieve.coercions");
            templates = map.get(ft.getDestinationAET());
            t1 = System.currentTimeMillis();
            for (FileCache fc : fileCacheList) {
                if (as.isReadyForDataTransfer()) {
                    String fpath = fc.getFilePath();
                    String cuid = fc.getSopClassUID();
                    String iuid = fc.getSopInstanceUID();
                    String ts = fc.getTransferSyntaxUID();
                    try {
                        send(new File(fpath), cuid, iuid, ts);
                        send++;
                    } catch (NoPresentationContextException e) {
                        LOG.error(e.getMessage());
                    }
                }
            }
            t2 = System.currentTimeMillis();
            writeAuditLog(ft, t2 - t1, send);
            forwardTaskMgr.remove(ft.getPk());
        } catch (Exception e) {
            LOG.error(e.getMessage());
            forwardTaskMgr.updateForwardTaskStatus(ForwardTaskStatus.FAILED, e.getMessage(), 
                    ft.getPk());
            try {
                as.waitForOutstandingRSP();
            } catch (InterruptedException ie) {
                LOG.error(ie.getMessage());
            }
        }
    }

    private void writeAuditLog(ForwardTask ft, long duration, long send) {
        AuditLog log = new AuditLog();
        log.setDestinationAET(ft.getDestinationAET());
        log.setFilesSend(send);
        log.setSeriesInstanceUID(ft.getSeriesInstanceUID());
        log.setSourceAET(ft.getSourceAET());
        log.setTransferTime(duration);
        auditLogMgr.persist(log);
    }
    
    public void send(final File f, String cuid, String iuid,
            String ts) throws IOException, InterruptedException {
        DicomInputStream in = new DicomInputStream(f);
        in.readFileMetaInformation();
        DataWriter data = createDataWriter(f);
        DimseRSPHandler rspHandler = new DimseRSPHandler(as.nextMessageID()) {
            @Override
            public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                super.onDimseRSP(as, cmd, data);
                onCStoreRSP(cmd, f);
            }
        };
        as.cstore(cuid, iuid, priority, data , ts, rspHandler);
    }
    
    private void onCStoreRSP(Attributes cmd, File f) {
        int status = cmd.getInt(Tag.Status, -1);
        switch (status) {
        case 0:
            totalSize += f.length();
            ++filesSent;
            break;
        case 0xB000:
        case 0xB006:
        case 0xB007:
            totalSize += f.length();
            ++filesSent;
            LOG.error(cmd.toString());
            break;
        default:
            LOG.error(cmd.toString());
        }
    }
    
    public void close() throws IOException, InterruptedException {
        if (as != null && as.isReadyForDataTransfer())
            as.release();
    }
    
    protected DataWriter createDataWriter(File file)
    throws IOException {
        Attributes attrs;
        DicomInputStream in = new DicomInputStream(file);
        try {
            in.setIncludeBulkDataLocator(true);
            attrs = in.readDataset(-1, -1);
        } finally {
            SafeClose.close(in);
        }
        
        if (templates != null) {
            Attributes modify = new Attributes();
            try {
                SAXWriter w = SAXTransformer.getSAXWriter(templates, modify);
                w.setIncludeKeyword(false);
                w.write(attrs);
            } catch (Exception e) {
                new IOException(e);
            }
            attrs.addAll(modify);
        }
        return new DataWriterAdapter(attrs);
    }
}
