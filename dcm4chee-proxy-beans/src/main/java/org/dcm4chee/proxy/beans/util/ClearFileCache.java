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

package org.dcm4chee.proxy.beans.util;

import java.io.File;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.ejb.EJB;

import org.dcm4che.net.Device;
import org.dcm4chee.proxy.ejb.FileCacheManager;
import org.dcm4chee.proxy.persistence.FileCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * 
 */
public class ClearFileCache {
    
    Timer timer;
    
    private Device device;
    
    private static final Logger LOG =
        LoggerFactory.getLogger(ClearFileCache.class);
    
    @EJB
    private FileCacheManager fileCacheMgr;
    
    private ScheduledFuture<?> clearFileCacheRSP;
    
    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }
    
    class RemoveUnknownDestinationData implements Runnable {
        public void run() {
            List<FileCache> fileCacheList = fileCacheMgr.findByFilesetUIDNotInForwardTask();
            for (FileCache fileCache : fileCacheList) {
                File file = new File(fileCache.getFilePath());
                try {
                    if (file.delete()) {
                        LOG.info("Deleted " + file);
                        fileCacheMgr.remove(fileCache.getPk());
                    }
                    else
                        LOG.error("Error deleting " + file);
                } catch (RuntimeException e) {
                    LOG.error(e.getMessage());
                }
            }
        }
    }
    
    public void startClearFileCacheRSP() {
        int timerInterval = (Integer) device.getProperty("Interval.clearFileCache");
        clearFileCacheRSP = device.scheduleAtFixedRate(new RemoveUnknownDestinationData(),
                timerInterval, timerInterval, TimeUnit.SECONDS);
        LOG.info("Started clearFileCacheRSP with " + timerInterval + " seconds interval");
    }
    
    public void stopClearFileCacheRSP() {
        if (clearFileCacheRSP != null)
            clearFileCacheRSP.cancel(false);
    }
}
