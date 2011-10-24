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

import java.util.Calendar;
import java.util.List;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TimerService;
import javax.jms.JMSException;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.dcm4che.util.UIDUtils;
import org.dcm4chee.proxy.ejb.FileCacheManagerBean.AESchedule;
import org.dcm4chee.proxy.persistence.ForwardTask;
import org.dcm4chee.proxy.persistence.ForwardTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * 
 */
@Stateless
public class ForwardTaskManagerBean implements ForwardTaskManager {
    
    private static final Logger LOG =
        LoggerFactory.getLogger(ForwardTaskManagerBean.class);
    
    @EJB
    private FileCacheManager fileCacheMgr;
    
    @EJB
    private DeviceHolder device;
    
    @Resource
    TimerService timerService;
    
    @Resource(mappedName="java:/JmsXA")
    private QueueConnectionFactory qconFactory;
    
    @Resource(mappedName="queue/ForwardTaskQueue")
    private Queue queue;
    
    @PersistenceContext(unitName = "dcm4chee-proxy")
    private EntityManager em;
    
    @Override
    public void persist(ForwardTask forwardTask) {
        em.persist(forwardTask);
    }
    
    @Override
    public void remove(int pk) {
        ForwardTask forwardTask = em.find(ForwardTask.class, pk);
        em.remove(forwardTask);
    }
    
    @Override
    public void scheduleForwardTask(String seriesIUID, String sourceAET, 
            List<AESchedule> destinationAETs) throws JMSException {
        String fsUID = UIDUtils.createUID();
        fileCacheMgr.setFilesetUID(fsUID, seriesIUID, sourceAET);
        for (AESchedule destinationAET : destinationAETs) {
            ForwardTask ft = storeForwardTask(fsUID, destinationAET.AETitle);
            sendStoreSCPMessage(ft , destinationAET.scheduleTime);
        }
    }
    
    @Override
    public void rescheduleFailedForwardTasks() throws JMSException {
        int forwardScheduleInterval = 
            (Integer) device.getDevice().getProperty("Interval.forwardSchedule");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, forwardScheduleInterval);
        List<ForwardTask> ftl = findForwardTaskByStatus(ForwardTaskStatus.FAILED);
        if (!ftl.isEmpty()) {
            for (ForwardTask ft : findForwardTaskByStatus(ForwardTaskStatus.FAILED)){
                LOG.debug("Rescheduling " + ft.getFilesetUID() + " in " + forwardScheduleInterval + " seconds.");
                sendStoreSCPMessage(ft, cal.getTimeInMillis());
            }
        }
    }
    
    @Override
    public int updateForwardTaskStatus(ForwardTaskStatus status, String error, int pk) {
        return em.createNamedQuery(ForwardTask.UPDATE_STATUS)
            .setParameter(1, status)
            .setParameter(2, error)
            .setParameter(3, pk)
            .executeUpdate();
    }
    
    @Override
    public void sendStoreSCPMessage(ForwardTask ft, long scheduleTime) throws JMSException {
        QueueConnection qcon = qconFactory.createQueueConnection();
        QueueSession qsession = qcon.createQueueSession(false, 0);
        QueueSender qsender = qsession.createSender(queue);
        ObjectMessage message = qsession.createObjectMessage(ft);
        if (scheduleTime > 0)
            message.setLongProperty("_JBM_SCHED_DELIVERY", scheduleTime);
        qsender.send(message);
        qsender.close();
        qsession.close();
        qcon.close();
    }
    
    @Override
    public List<ForwardTask> findForwardTaskByStatus(ForwardTaskStatus status) {
        return em.createNamedQuery(ForwardTask.FIND_BY_STATUS_CODE, ForwardTask.class)
        .setParameter(1, status)
        .getResultList();
    }

    private ForwardTask storeForwardTask(String fsUID, String destinationAET) {
        ForwardTask ft = new ForwardTask();
        ft.setFilesetUID(fsUID);
        ft.setForwardTaskStatus(ForwardTaskStatus.SCHEDULED);
        ft.setDestinationAET(destinationAET);
        ft.setErrorCode("-");
        persist(ft);
        return ft;
    }
}
