/**
 * Copyright 2015 Microsoft Open Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.microsoftopentechnologies.intellij.ui.libraries;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import com.microsoftopentechnologies.intellij.AzurePlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static com.microsoftopentechnologies.intellij.ui.messages.AzureBundle.message;
/**
 * Handler class for creating and modifying acs filter components
 */
public class ACSFilterHandler {

    Document doc = null;
    String xmlPath = "";

    /**
     * Constructor.
     * @param webXmlPath
     * @throws Exception
     */
    ACSFilterHandler(String webXmlPath) throws Exception {
        try {
            xmlPath = webXmlPath;
            File xmlFile = new File(webXmlPath);
            if (xmlFile.exists()) {
                doc = ParserXMLUtility.parseXMLFile(webXmlPath, message("acsErrMsg"));
            } else {
                throw new Exception(String.format("%s%s", webXmlPath, message("fileErrMsg")));
            }
        } catch (Exception e) {
            AzurePlugin.log(e.getMessage(), e);
            throw new Exception(message("acsErrMsg"));
        }
    }

    /**
     * This method sets the ACS filter attributes.
     * @param pName
     * @param pValue
     * @throws Exception
     */
    public void setAcsFilterParams(String pName, String pValue) throws Exception {
        if ((pName == null) || pName.isEmpty() || (pValue == null) || pValue.isEmpty()) {
            throw new IllegalArgumentException();
        }
        try {
            //Check Filter tag is present or not. If not exist create new.
            String exprFilter = message("acsExprConst");
            XPath xpath = XPathFactory.newInstance().newXPath();
            Element eleFilter = (Element) xpath.evaluate(exprFilter, doc, XPathConstants.NODE);
            if (eleFilter == null) {
                Element filter = doc.createElement(message("filterTag"));
                Element filterName = doc.createElement(message("filterEle"));
                filterName.setTextContent(message("acsfilter"));
                filter.appendChild(filterName);

                Element fClass = doc.createElement("filter-class");
                fClass.setTextContent(message("acsClassName"));
                filter.appendChild(fClass);

                eleFilter = (Element) doc.getDocumentElement().appendChild(filter);
            }
            setFilterMapping();

            //check pName is already exist or not
            String exprAcsParm = String.format(message("exprAcsPName"), pName);
            Element initParam = (Element) xpath.evaluate(exprAcsParm, doc, XPathConstants.NODE);

            if (initParam == null) {
                initParam = doc.createElement(message("initPar"));
                Element paramName = doc.createElement(message("parName"));
                Element paramval = doc.createElement(message("parVal"));
                paramName.setTextContent(pName);
                paramval.setTextContent(pValue);
                initParam.appendChild(paramName);
                initParam.appendChild(paramval);
                eleFilter.appendChild(initParam);
            } else {
                String strParamVal = "./" + message("parVal");
                Element paramVal = (Element) xpath.evaluate(strParamVal, initParam, XPathConstants.NODE);
                paramVal.setTextContent(pValue);
            }
        } catch (Exception ex) {
            AzurePlugin.log(ex.getMessage(), ex);
            throw new Exception(String.format("%s%s", message("acsParamErr"), ex.getMessage()));
        }
    }

    /**
     * This method removes ACS filter attribute if exists.
     * @throws Exception
     */
    public void removeParamsIfExists(String pName) throws Exception {
        if ((pName == null) || pName.isEmpty()) {
            throw new IllegalArgumentException();
        }
        try {
            //check pName already exists or not
            XPath xpath = XPathFactory.newInstance().newXPath();
            String exprAcsParm = String.format(message("exprAcsPName"), pName);
            Element initParam = (Element) xpath.evaluate(exprAcsParm, doc, XPathConstants.NODE);

            if (initParam != null)
                initParam.getParentNode().removeChild(initParam);
        } catch (Exception ex) {
            AzurePlugin.log(ex.getMessage(), ex);
            throw new Exception(String.format("%s%s", message("acsParamErr"), ex.getMessage()));
        }
    }

    /**
     * This method adds filter mapping tags in web.xml.
     */
    private void setFilterMapping() {
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            Element eleFltMapping = (Element) xpath.evaluate(message("exprFltMapping"), doc, XPathConstants.NODE);
            if (eleFltMapping == null) {
                Element filterMapping = doc.createElement(message("filterMapTag"));
                Element filterName = doc.createElement(message("filterEle"));
                filterName.setTextContent(message("acsfilter"));
                filterMapping.appendChild(filterName);

                Element urlPattern = doc.createElement(message("urlPatternTag"));
                urlPattern.setTextContent("/*");
                filterMapping.appendChild(urlPattern);
                doc.getDocumentElement().appendChild(filterMapping);
            }
        } catch (Exception ex) {
            AzurePlugin.log(ex.getMessage(), ex);
        }

    }

    /**
     * This method is to get all ACS related parameters.
     * @return
     * @throws Exception
     */
    public HashMap<String, String> getAcsFilterParams() throws Exception {
        try {
            HashMap<String, String> fltParams = new HashMap<String, String>();
            String exprAcsParm = message("exprAcsParam");
            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList params = (NodeList) xpath.evaluate(exprAcsParm, doc, XPathConstants.NODESET);
            String paramName = "./param-name/text()";
            String paramVal = "./param-value/text()";
            if (params != null) {
                for (int i = 0; i < params.getLength(); i++) {
                    Element param = (Element) params.item(i);
                    fltParams.put(xpath.evaluate(paramName, param), xpath.evaluate(paramVal, param));
                }
            }
            return fltParams;
        } catch (Exception ex) {
            AzurePlugin.log(ex.getMessage(), ex);
            throw new Exception(String.format("%s%s", message("acsGetParamErr"), ex.getMessage()));
        }
    }


    /**
     * This method saves the web.xml changes.
     * @throws IOException
     * @throws Exception
     */
    public void save() throws IOException, Exception {
        ParserXMLUtility.saveXMLFile(xmlPath, doc);
    }

    /**
     * This method remove all ACS related settings from Web.xml.
     * @throws Exception
     */
    public void remove() throws Exception {
        try {
            String exprFilter = message("acsExprConst");
            XPath xpath = XPathFactory.newInstance().newXPath();
            Element eleFilter = (Element) xpath.evaluate(exprFilter, doc, XPathConstants.NODE);
            if (eleFilter != null) {
                eleFilter.getParentNode().removeChild(eleFilter);
            }
            String exprFltMapping = message("exprFltMapping");
            Element eleFilMapping = (Element) xpath.evaluate(exprFltMapping, doc, XPathConstants.NODE);
            if (eleFilMapping != null) {
                eleFilMapping.getParentNode().removeChild(eleFilMapping);
            }
        } catch (Exception ex) {
            AzurePlugin.log(ex.getMessage(), ex);
            throw new Exception(String.format("%s%s", message("acsRemoveErr"), ex.getMessage()));
        }
    }
}
