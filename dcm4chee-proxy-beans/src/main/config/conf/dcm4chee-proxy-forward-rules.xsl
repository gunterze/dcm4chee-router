<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:param name="sourceAET">STORESCU</xsl:param>
  <xsl:template match="/">
    <xsl:variable name="requestingPhysicianFamilyName"
      select="/NativeDicomModel/DicomAttribute[@tag='00400275']/Item/DicomAttribute[@tag='00321032']/PersonName/Alphabetic/FamilyName"/>
    <Destinations>
      <xsl:choose>
        <xsl:when test="$requestingPhysicianFamilyName='Bowman'">
          <Destination aet="DISCOVERY-ONE"/>
       </xsl:when>
      </xsl:choose>
      <xsl:choose>
        <xsl:when test="$sourceAET='STORESCU'">
         <Destination aet="STORESCP" dayOfWeek="Mon-Fri" hour="08-18" />
         <Destination aet="STORESCP_TLS" dayOfWeek="Mon-Fri" hour="08-18" />
        </xsl:when>
      </xsl:choose>
    </Destinations>
  </xsl:template>
</xsl:stylesheet>
