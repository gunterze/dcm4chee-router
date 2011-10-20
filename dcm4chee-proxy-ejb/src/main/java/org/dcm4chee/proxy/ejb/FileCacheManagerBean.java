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

package org.dcm4chee.proxy.ejb;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.ScheduleExpression;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.JMSException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

import org.dcm4che.data.Tag;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.SAXWriter;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.proxy.persistence.FileCache;
import org.dcm4chee.proxy.persistence.ForwardTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@Stateless
public class FileCacheManagerBean implements FileCacheManager {
    
    private static final Logger LOG =
        LoggerFactory.getLogger(ForwardTaskStatus.class);

    @EJB
    private ForwardTaskManager forwardTaskMgr;
    
    @EJB
    private DeviceHolder device;

    @Resource
    TimerService timerService;

    @PersistenceContext(unitName = "dcm4chee-proxy")
    private EntityManager em;

    @Override
    public void persist(FileCache fileCache) {
        em.persist(fileCache);
    }
    
    @Override
    public void remove(int pk) {
        FileCache fileCache = em.find(FileCache.class, pk);
        em.remove(fileCache);
    }
    
    public List<String> findSeriesReceivedBefore(Date before) {
        return em.createNamedQuery(FileCache.FIND_SERIES_RECEIVED_BEFORE, String.class)
            .setParameter(1, FileCache.NO_FILESET_UID)
            .setParameter(2, before)
            .getResultList();
    }

    public List<String> findSourceAETsOfSeries(String seriesIUID) {
        return em.createNamedQuery(FileCache.FIND_SOURCE_AETS_OF_SERIES, String.class)
            .setParameter(1, FileCache.NO_FILESET_UID)
            .setParameter(2, seriesIUID)
            .getResultList();
    }

    @Override
    public List<FileCache> findByFilesetUID(String fsUID) {
        return em.createNamedQuery(FileCache.FIND_BY_FILESET_UID, FileCache.class)
            .setParameter(1, fsUID)
            .getResultList();
    }
    
    @Override
    public List<FileCache> findByFilesetUIDNotInForwardTask() {
        return em.createNamedQuery(FileCache.FIND_FILESET_UID_NOT_IN_FT, FileCache.class)
                .getResultList();
    }

    @Override
    public int setFilesetUID(String fsUID, String seriesIUID, String sourceAET) {
        return em.createNamedQuery(FileCache.UPDATE_FILESET_UID)
            .setParameter(1, fsUID)
            .setParameter(2, FileCache.NO_FILESET_UID)
            .setParameter(3, seriesIUID)
            .setParameter(4, sourceAET)
            .executeUpdate();
    }
    
    @Override
    public Timer initTimer(){
        TimerConfig timerConfig = new TimerConfig();
        timerConfig.setPersistent(false);
        ScheduleExpression schedule = new ScheduleExpression();
        int timerInterval = (Integer) device.getDevice()
                .getProperty("Interval.checkForNewReceivedSeries")*1000;
        schedule.second(timerInterval);
        LOG.info("Creating checkForNewReceivedSeriesTimer with " + timerInterval/1000 
                + " seconds interval");
        return timerService.createIntervalTimer(timerInterval, timerInterval, timerConfig);
    }
    

    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void checkForNewReceivedSeries() throws JMSException, TransformerException, IOException {
        Calendar interval = Calendar.getInstance();
        int timerInterval = (Integer) device.getDevice()
                .getProperty("Interval.checkForNewReceivedSeries");
        interval.add(Calendar.SECOND, -timerInterval);
        List<String> newSeriesList = findSeriesReceivedBefore(interval.getTime());
        if (!newSeriesList.isEmpty()){
            SAXTransformerFactory transFac = (SAXTransformerFactory) TransformerFactory.newInstance();
            TransformerHandler handler = transFac.newTransformerHandler(
                    (Templates) device.getDevice().getProperty("ForwardRules"));
            final ArrayList<String> destinationAETs = new ArrayList<String>();
            handler.setResult(new SAXResult(new DefaultHandler(){
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes)
                        throws SAXException {
                    if (qName.equals("Destination"))
                        destinationAETs.add(attributes.getValue("aet"));
                }
            }));
            Transformer trans = handler.getTransformer();
            for (String seriesIUID : newSeriesList) {
                List<String> sourceAETs = findSourceAETsOfSeries(seriesIUID);
                for (String sourceAET : sourceAETs) {
                    getDestinationParameters(handler, trans, seriesIUID, sourceAET);
                    if (!destinationAETs.isEmpty())
                        forwardTaskMgr.scheduleForwardTask(seriesIUID, sourceAET, destinationAETs);
                    else
                        LOG.error("No destination AETs defined for " + sourceAET);
                }
            }
        }
    }

    private void getDestinationParameters(TransformerHandler handler, Transformer trans,
            String seriesIUID, String sourceAET) throws IOException {
        trans.setParameter("sourceAET", sourceAET);
        List<FileCache> fcl =
                em.createNamedQuery(FileCache.FIND_BY_SERIES_UID, FileCache.class)
                        .setParameter(1, seriesIUID)
                        .setMaxResults(1)
                        .getResultList();
        DicomInputStream dis = new DicomInputStream(
                new File(((FileCache)fcl.get(0)).getFilePath()));
        try {
            dis.setIncludeBulkData(false);
            SAXWriter writer = new SAXWriter(handler);
//            writer.setIncludeKeyword(false);
            dis.setDicomInputHandler(writer);
            dis.readDataset(-1, Tag.PixelData);
        } finally {
            SafeClose.close(dis);
        }
    }
}
