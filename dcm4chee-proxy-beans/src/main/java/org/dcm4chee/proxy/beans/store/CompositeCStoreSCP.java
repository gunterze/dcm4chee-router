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

package org.dcm4chee.proxy.beans.store;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.MessageDigest;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Status;
import org.dcm4che.net.service.BasicCStoreSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.proxy.beans.util.JNDIUtils;
import org.dcm4chee.proxy.ejb.FileCacheManager;
import org.dcm4chee.proxy.persistence.FileCache;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class CompositeCStoreSCP extends BasicCStoreSCP {

    private FileCacheManager cacheMgr;
 
    public CompositeCStoreSCP(String... sopClasses) {
        super(sopClasses);
    }

    @Override
    protected File createFile(Association as, Attributes rq, Object storage)
            throws DicomServiceException {
        try {
            ApplicationEntity ae = as.getApplicationEntity();
            String cacheDirPath = (String) ae.getProperty("dcm4chee.proxy.cache.path");
            String sourceAET = as.getRemoteAET();
            File tmpDir = new File(cacheDirPath, URLEncoder.encode(sourceAET, "UTF-8"));
            tmpDir.mkdirs();
            return File.createTempFile("dcm", ".dcm", tmpDir);
        } catch (Exception e) {
            LOG.warn(as + ": Failed to create temp file:", e);
            throw new DicomServiceException(rq, Status.OutOfResources, e);
        }
    }

    @Override
    protected File process(Association as, Attributes rq, String tsuid, Attributes rsp,
            Object storage, File file, MessageDigest digest) throws DicomServiceException {
        Attributes ds = readDataset(as, rq, file);
        FileCache fc = new FileCache();
        fc.setSourceAET(as.getRemoteAET());
        fc.setSeriesInstanceUID(ds.getString(Tag.SeriesInstanceUID));
        fc.setSOPInstanceUID(ds.getString(Tag.SOPInstanceUID));
        fc.setSOPClassUID(ds.getString(Tag.SOPClassUID));
        fc.setTransferSyntaxUID(tsuid);
        fc.setFilePath(file.getPath());
        fc.setFilesetID(FileCache.NO_FILESET_ID);
        try {
            if (cacheMgr == null)
                cacheMgr = (FileCacheManager)
                        JNDIUtils.lookup(FileCacheManager.JNDI_NAME);
            cacheMgr.persist(fc);
        } catch (Exception e) {
            throw new DicomServiceException(rq,
                    Status.OutOfResources, causeOf(e));
        }
        return null;
    }

    private Attributes readDataset(Association as, Attributes rq, File file)
            throws DicomServiceException {
        DicomInputStream in = null;
        try {
            in = new DicomInputStream(file);
            in.setIncludeBulkData(false);
            return in.readDataset(-1, Tag.PixelData);
        } catch (IOException e) {
            LOG.warn(as + ": Failed to decode dataset:", e);
            throw new DicomServiceException(rq, Status.CannotUnderstand);
        } finally {
            SafeClose.close(in);
        }
    }

    private static Throwable causeOf(Throwable e) {
        Throwable cause;
        while ((cause = e.getCause()) != null && e != cause)
            e = cause;
        return e;
    }
}
