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

package org.dcm4chee.proxy.persistence;

import java.util.Date;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Entity
@Table(name = "instance_cache")
public class InstanceCache {

    private static final String NONE = "-";

    @Id
    @GeneratedValue
    @Column(name = "pk")
    private int pk;

    @Basic(optional = false)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Column(name = "study_iuid", updatable = false)
    private String studyInstanceUID;

    @Basic(optional = false)
    @Column(name = "series_iuid", updatable = false)
    private String seriesInstanceUID;

    @Basic(optional = false)
    @Column(name = "sop_iuid", updatable = false)
    private String sopInstanceUID;

    @Basic(optional = false)
    @Column(name = "sop_cuid", updatable = false)
    private String sopClassUID;

    @Basic(optional = false)
    @Column(name = "file_tsuid", updatable = false)
    private String transferSyntaxUID;

    @Basic(optional = false)
    @Column(name = "file_path", updatable = false)
    private String filePath;

    @Basic(optional = false)
    @Column(name = "src_aet", updatable = false)
    private String sourceAET;

    @Basic(optional = true)
    @Column(name = "fileset_id")
    private String filesetID;

    public InstanceCache() {}

    public InstanceCache(Attributes fmi, Attributes ds, String filePath) {
        this.studyInstanceUID = ds.getString(Tag.StudyInstanceUID);
        this.seriesInstanceUID = ds.getString(Tag.SeriesInstanceUID);
        this.sopInstanceUID = fmi.getString(Tag.MediaStorageSOPInstanceUID);
        this.sopClassUID = fmi.getString(Tag.MediaStorageSOPClassUID);
        this.transferSyntaxUID = fmi.getString(Tag.TransferSyntaxUID);
        this.sourceAET = fmi.getString(Tag.SourceApplicationEntityTitle);
        this.filesetID = NONE;
        this.filePath = filePath;
    }
    
    @PrePersist
    public void onPrePersist() {
        createdTime = new Date();
    }

    public int getPk() {
        return pk;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public String getFilesetID() {
        return filesetID;
    }

    public void setFilesetID(String filesetID) {
        this.filesetID = filesetID;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getSeriesInstanceUID() {
        return seriesInstanceUID;
    }

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    public String getSopClassUID() {
        return sopClassUID;
    }

    public String getTransferSyntaxUID() {
        return transferSyntaxUID;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSourceAET() {
        return sourceAET;
    }

}
