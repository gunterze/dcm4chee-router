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
import java.util.Date;
import java.util.List;
import java.util.Map;

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

import org.dcm4chee.proxy.persistence.FileCache;
import org.dcm4chee.proxy.persistence.ForwardTaskStatus;
import org.dcm4chee.proxy.persistence.QFileCache;
import org.dcm4chee.proxy.persistence.QForwardTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mysema.query.hql.HQLQuery;
import com.mysema.query.hql.HQLSubQuery;
import com.mysema.query.hql.hibernate.HibernateQuery;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@Stateless
public class FileCacheManagerBean implements FileCacheManager {
    
    private static final Logger LOG =
        LoggerFactory.getLogger(ForwardTaskStatus.class);

    private int timerInterval;
    private Timer timer;
    
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

    public List<FileCache> findByFilesetUID(String fsUID) {
        return em.createNamedQuery(FileCache.FIND_BY_FILESET_UID, FileCache.class)
            .setParameter(1, fsUID)
            .getResultList();
    }
    
    @Override
    public List<FileCache> findByFilesetUIDNotInForwardTask() {
        QFileCache fileCache = QFileCache.fileCache;
        QForwardTask forwardTask = QForwardTask.forwardTask;
        HQLQuery query = new HibernateQuery();
        return query.from(fileCache)
                .where(fileCache.filesetUID.ne("-").and(fileCache.filesetUID.notIn(
                        new HQLSubQuery().from(forwardTask).list(forwardTask.filesetUID))))
                .list(fileCache);
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
    public void initTimer(){
        TimerConfig timerConfig = new TimerConfig();
        timerConfig.setPersistent(false);
        ScheduleExpression schedule = new ScheduleExpression();
        timerInterval = (Integer) device.getDevice().getProperty("timerInterval")*1000;
        schedule.second(timerInterval);
        LOG.info("Creating timer with " + timerInterval/1000 + " seconds interval");
        timer = timerService.createIntervalTimer(timerInterval, timerInterval, timerConfig);
    }
    
    @Override
    public void cancelTimer() {
        LOG.info("Canceling timer");
        timer.cancel();
    }

    @SuppressWarnings("unchecked")
    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void checkForNewReceivedSeries() throws JMSException {
        Calendar interval = Calendar.getInstance();
        interval.add(Calendar.MINUTE, -timerInterval);
        List<String> newSeriesList =
                findSeriesReceivedBefore(interval.getTime());
        for (String seriesIUID : newSeriesList) {
            List<String> sourceAETs = findSourceAETsOfSeries(seriesIUID);
            for (String sourceAET : sourceAETs) {
                Map<String, String[]> forwardRules =
                        (Map<String, String[]>) device.getDevice().getProperty("ForwardRules");
                String[] destinationAETs = forwardRules.get(sourceAET);
                if (destinationAETs.length > 0)
                    forwardTaskMgr.scheduleForwardTask(seriesIUID, sourceAET, destinationAETs);
                else
                    LOG.info("No destination AETs defined for " + sourceAET);
            }
        }
    }
}
