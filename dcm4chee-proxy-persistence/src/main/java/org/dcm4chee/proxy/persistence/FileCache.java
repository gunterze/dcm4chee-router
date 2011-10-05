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
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@NamedQueries({
    @NamedQuery(
        name="FileCache.findSeriesReceivedBefore",
        query="SELECT f.seriesInstanceUID FROM FileCache f WHERE f.filesetUID = ?1 "
            + "AND f.errorCode = ?2 GROUP BY f.seriesInstanceUID HAVING MAX(f.createdTime) < ?3"),
    @NamedQuery(
        name="FileCache.findSourceAETsOfSeries",
        query="SELECT DISTINCT f.sourceAET FROM FileCache f WHERE f.filesetUID = ?1 "
            + "AND f.seriesInstanceUID = ?2"),
    @NamedQuery(
        name="FileCache.findByFilesetUID",
        query="SELECT f FROM FileCache f WHERE f.filesetUID = ?1"),
    @NamedQuery(
        name="FileCache.updateFilesetUID",
        query="UPDATE FileCache f SET filesetUID = ?1 WHERE f.filesetUID = ?2 "
            + "AND f.seriesInstanceUID = ?3 AND f.sourceAET = ?4"),
    @NamedQuery(
        name="FileCache.updateErrorCode",
        query="UPDATE FileCache f SET errorCode = ?1 WHERE f.errorCode = ?2 "
            + "AND f.seriesInstanceUID = ?3 AND f.sourceAET = ?4")
    })
@Entity
@Table(name = "file_cache")
public class FileCache {

    public static final String NO_FILESET_UID = "-";
    public static final String NO_ERROR_CODE = "-";
    public static final String NO_TARGET_AET = "Error: no target AET configured";
    public static final String FIND_SERIES_RECEIVED_BEFORE = "FileCache.findSeriesReceivedBefore";
    public static final String FIND_SOURCE_AETS_OF_SERIES = "FileCache.findSourceAETsOfSeries";
    public static final String FIND_BY_FILESET_UID = "FileCache.findByFilesetUID";
    public static final String UPDATE_FILESET_UID = "FileCache.updateFilesetUID";
    public static final String UPDATE_ERROR_CODE = "FileCache.updateErrorCode";

    @Id
    @GeneratedValue
    @Column(name = "pk")
    private int pk;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Column(name = "src_aet", updatable = false)
    private String sourceAET;

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
    @Column(name = "fileset_uid")
    private String filesetUID;
    
    @Basic(optional = true)
    @Column(name = "error_code")
    private String errorCode;

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

    public String getSourceAET() {
        return sourceAET;
    }

    public void setSourceAET(String sourceAET) {
        this.sourceAET = sourceAET;
    }

    public String getSeriesInstanceUID() {
        return seriesInstanceUID;
    }

    public void setSeriesInstanceUID(String seriesInstanceUID) {
        this.seriesInstanceUID = seriesInstanceUID;
    }

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    public void setSOPInstanceUID(String sopInstanceUID) {
        this.sopInstanceUID = sopInstanceUID;
    }

    public String getSopClassUID() {
        return sopClassUID;
    }

    public void setSOPClassUID(String sopClassUID) {
        this.sopClassUID = sopClassUID;
    }

    public String getTransferSyntaxUID() {
        return transferSyntaxUID;
    }

    public void setTransferSyntaxUID(String transferSyntaxUID) {
        this.transferSyntaxUID = transferSyntaxUID;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFilesetUID() {
        return filesetUID;
    }

    public void setFilesetUID(String filesetUID) {
        this.filesetUID = filesetUID;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

}
