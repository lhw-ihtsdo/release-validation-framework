//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2017.09.28 at 02:15:54 PM BST 
//


package org.ihtsdo.rvf.validation.model.manifest;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 * <p>Java class for fileType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="fileType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="contains-reference-sets" type="{http://release.ihtsdo.org/manifest/1.0.0}containsReferenceSetsType" minOccurs="0"/>
 *         &lt;element name="contains-language-codes" type="{http://release.ihtsdo.org/manifest/1.0.0}containsLanguageCodesType" minOccurs="0"/>
 *         &lt;element name="contains-additional-fields" type="{http://release.ihtsdo.org/manifest/1.0.0}containsAdditionalFieldsType" minOccurs="0"/>
 *         &lt;element name="sources" type="{http://release.ihtsdo.org/manifest/1.0.0}sourcesType" minOccurs="0"/>
 *       &lt;/all>
 *       &lt;attribute name="Name" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "fileType", propOrder = {

})
public class FileType {

    @XmlElement(name = "contains-reference-sets")
    protected ContainsReferenceSetsType containsReferenceSets;
    @XmlElement(name = "contains-language-codes")
    protected ContainsLanguageCodesType containsLanguageCodes;
    @XmlElement(name = "contains-additional-fields")
    protected ContainsAdditionalFieldsType containsAdditionalFields;
    protected SourcesType sources;
    @XmlAttribute(name = "Name")
    protected String name;

    /**
     * Gets the value of the containsReferenceSets property.
     * 
     * @return
     *     possible object is
     *     {@link ContainsReferenceSetsType }
     *     
     */
    public ContainsReferenceSetsType getContainsReferenceSets() {
        return containsReferenceSets;
    }

    /**
     * Sets the value of the containsReferenceSets property.
     * 
     * @param value
     *     allowed object is
     *     {@link ContainsReferenceSetsType }
     *     
     */
    public void setContainsReferenceSets(ContainsReferenceSetsType value) {
        this.containsReferenceSets = value;
    }

    /**
     * Gets the value of the containsLanguageCodes property.
     * 
     * @return
     *     possible object is
     *     {@link ContainsLanguageCodesType }
     *     
     */
    public ContainsLanguageCodesType getContainsLanguageCodes() {
        return containsLanguageCodes;
    }

    /**
     * Sets the value of the containsLanguageCodes property.
     * 
     * @param value
     *     allowed object is
     *     {@link ContainsLanguageCodesType }
     *     
     */
    public void setContainsLanguageCodes(ContainsLanguageCodesType value) {
        this.containsLanguageCodes = value;
    }

    /**
     * Gets the value of the containsAdditionalFields property.
     * 
     * @return
     *     possible object is
     *     {@link ContainsAdditionalFieldsType }
     *     
     */
    public ContainsAdditionalFieldsType getContainsAdditionalFields() {
        return containsAdditionalFields;
    }

    /**
     * Sets the value of the containsAdditionalFields property.
     * 
     * @param value
     *     allowed object is
     *     {@link ContainsAdditionalFieldsType }
     *     
     */
    public void setContainsAdditionalFields(ContainsAdditionalFieldsType value) {
        this.containsAdditionalFields = value;
    }

    /**
     * Gets the value of the sources property.
     * 
     * @return
     *     possible object is
     *     {@link SourcesType }
     *     
     */
    public SourcesType getSources() {
        return sources;
    }

    /**
     * Sets the value of the sources property.
     * 
     * @param value
     *     allowed object is
     *     {@link SourcesType }
     *     
     */
    public void setSources(SourcesType value) {
        this.sources = value;
    }

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

}
