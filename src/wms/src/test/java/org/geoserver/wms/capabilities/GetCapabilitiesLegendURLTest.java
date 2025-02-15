/* Copyright (c) 2014 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.capabilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.config.impl.ContactInfoImpl;
import org.geoserver.config.impl.GeoServerImpl;
import org.geoserver.config.impl.GeoServerInfoImpl;
import org.geoserver.data.test.MockData;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.data.test.SystemTestData.LayerProperty;
import org.geoserver.ows.LocalWorkspace;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Paths;
import org.geoserver.platform.resource.Resource;
import org.geoserver.wms.GetCapabilitiesRequest;
import org.geoserver.wms.WMS;
import org.geoserver.wms.WMSInfo;
import org.geoserver.wms.WMSInfoImpl;
import org.geoserver.wms.WMSTestSupport;
import org.geoserver.wms.wms_1_1_1.GetFeatureInfoTest;
import org.geotools.xml.transform.TransformerBase;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Base class for legendURL support in GetCapabilities tests.
 * 
 * @author Mauro Bartolomeoli (mauro.bartolomeoli at geo-solutions.it)
 *
 */
public abstract class GetCapabilitiesLegendURLTest extends WMSTestSupport {

    /** default base url to feed a GetCapabilitiesTransformer with for it to append the DTD location */
    protected static final String baseUrl = "http://localhost/geoserver";
    
    /** test map formats to feed a GetCapabilitiesTransformer with */
    protected static final Set<String> mapFormats = Collections.singleton("image/png");
    
    /** test legend formats to feed a GetCapabilitiesTransformer with */
    protected static final Set<String> legendFormats = Collections.singleton("image/png");

    /**
     * a mocked up {@link GeoServer} config, almost empty after setUp(), except for the
     * {@link WMSInfo}, {@link GeoServerInfo} and empty {@link Catalog}, Specific tests should add
     * content as needed
     */
    protected GeoServerImpl geosConfig;
    
    /**
     * a mocked up {@link GeoServerInfo} for {@link #geosConfig}. Specific tests should set its
     * properties as needed
     */
    protected GeoServerInfoImpl geosInfo;
    
    /**
     * a mocked up {@link WMSInfo} for {@link #geosConfig}, empty except for the WMSInfo after
     * setUp(), Specific tests should set its properties as needed
     */
    protected WMSInfoImpl wmsInfo;
    
    /**
     * a mocked up {@link Catalog} for {@link #geosConfig}, empty after setUp(), Specific tests
     * should add content as needed
     */
    protected Catalog catalog;
    
    protected GetCapabilitiesRequest req;
    
    protected WMS wmsConfig;
    
    protected XpathEngine XPATH;
    
    /** Test layers */
    public static QName SQUARES = new QName(MockData.CITE_URI, "squares", MockData.CITE_PREFIX);
    public static QName STATES = new QName(MockData.CITE_URI, "states", MockData.CITE_PREFIX);
    
    
    /**
     * Adds required styles to test the selection of maximum and minimum denominator from style's rules.
     */
    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        this.catalog = getCatalog();
        File dataDirRoot = testData.getDataDirectoryRoot();
        // create legendsamples folder
        new File(dataDirRoot.getAbsolutePath() + File.separator
                + LegendSampleImpl.LEGEND_SAMPLES_FOLDER).mkdir();
        
        
        testData.addStyle("squares","squares.sld",GetFeatureInfoTest.class,catalog);
        testData.addVectorLayer(SQUARES,Collections.EMPTY_MAP,"squares.properties",
                GetCapabilitiesLegendURLTest.class,catalog);
        WorkspaceInfo workspaceInfo = catalog.getWorkspaceByName(MockData.CITE_PREFIX);
        testData.addStyle(workspaceInfo, "states","Population.sld",GetCapabilitiesLegendURLTest.class,catalog);
        Map<LayerProperty, Object> properties = new HashMap<LayerProperty, Object>();
        properties.put(LayerProperty.STYLE, "states");
        LocalWorkspace.set(workspaceInfo);
        testData.addVectorLayer(STATES,properties,"states.properties",
                GetCapabilitiesLegendURLTest.class,catalog);
        LocalWorkspace.set(null);
    }
    
    @Before
    public void internalSetUp() throws IOException {
        
        
        this.catalog = getCatalog();
        geosConfig = new GeoServerImpl();

        geosInfo = new GeoServerInfoImpl(geosConfig);
        geosInfo.setContact(new ContactInfoImpl());
        geosConfig.setGlobal(geosInfo);

        wmsInfo = new WMSInfoImpl();
        geosConfig.add(wmsInfo);

        geosConfig.setCatalog(catalog);

        wmsConfig = new WMS(geosConfig);
        wmsConfig.setApplicationContext(applicationContext);

        req = new GetCapabilitiesRequest();
        req.setBaseUrl(baseUrl);

        getTestData().copyTo(
                getClass().getResourceAsStream("/legendURL/BasicPolygons.png"),
                LegendSampleImpl.LEGEND_SAMPLES_FOLDER + "/BasicPolygons.png");
        getTestData().copyTo(getClass().getResourceAsStream("/legendURL/Bridges.png"),
                LegendSampleImpl.LEGEND_SAMPLES_FOLDER + "/Bridges.png");
        
        Map<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("wms", "http://www.opengis.net/wms");
        namespaces.put("ows", "http://www.opengis.net/ows");
        XMLUnit.setXpathNamespaceContext(new SimpleNamespaceContext(namespaces));
        XPATH = XMLUnit.newXpathEngine();
    }
    
    /**
     * Accessor for global catalog instance from the test application context.
     */
    protected Catalog getCatalog() {
        return (Catalog) applicationContext.getBean("catalog");
    }
    
    /**
     * Tests that already cached icons are read from disk and
     * used to calculate size.
     * @throws Exception
     */
    @Test
    public void testCachedLegendURLSize() throws Exception {
        
        TransformerBase tr = createTransformer();
        tr.setIndentation(2);
        Document dom = WMSTestSupport.transform(req, tr);
        
        NodeList legendURLs = XPATH.getMatchingNodes(
                getLegendURLXPath("cite:BasicPolygons"), dom);
        assertEquals(1, legendURLs.getLength());
        Element legendURL = (Element) legendURLs.item(0);
        assertTrue(legendURL.hasAttribute("width"));
        assertEquals("50", legendURL.getAttribute("width"));
        assertTrue(legendURL.hasAttribute("height"));
        assertEquals("10", legendURL.getAttribute("height"));
    }
    
    /**
     * Tests that folder for legend samples is created, if missing.
     * @throws Exception
     */
    @Test
    public void testCachedLegendURLFolderCreated() throws Exception {
        GeoServerResourceLoader loader = GeoServerExtensions.bean(GeoServerResourceLoader.class);
        
        File samplesFolder = new File(loader.getBaseDirectory().getAbsolutePath() + File.separator
                + LegendSampleImpl.LEGEND_SAMPLES_FOLDER);
        removeFileOrFolder(samplesFolder);
        TransformerBase tr = createTransformer();
        tr.setIndentation(2);
        Document dom = WMSTestSupport.transform(req, tr);
        
        assertTrue(samplesFolder.exists());
    }

    /**
     * Tests that not existing icons are created on disk and
     * used to calculate size.
     * @throws Exception
     */
    @Test
    public void testCreatedLegendURLSize() throws Exception {
        
        TransformerBase tr = createTransformer();
        tr.setIndentation(2);
        Document dom = WMSTestSupport.transform(req, tr);
        

        NodeList legendURLs = XPATH.getMatchingNodes(
                getLegendURLXPath("cite:squares"), dom); 
                
        assertEquals(1, legendURLs.getLength());
        Element legendURL = (Element) legendURLs.item(0);
        assertTrue(legendURL.hasAttribute("width"));
        assertFalse("20".equals(legendURL.getAttribute("width")));
        assertTrue(legendURL.hasAttribute("height"));
        assertFalse("20".equals(legendURL.getAttribute("height")));
        
        File sampleFile = getSampleFile("squares");
        assertTrue(sampleFile.exists());
    }

    private File getSampleFile(String sampleName) {
        return new File(testData.getDataDirectoryRoot().getAbsolutePath()
                + File.separator + LegendSampleImpl.LEGEND_SAMPLES_FOLDER + File.separator
                + sampleName + ".png");
    }
    
    /**
     * Tests that not existing icons for workspace bound styles are created on disk
     * in the workspace styles folder.
     * @throws Exception
     */
    @Test
    public void testCreatedLegendURLFromWorkspaceSize() throws Exception {
        
        TransformerBase tr = createTransformer();
        tr.setIndentation(2);
        Document dom = WMSTestSupport.transform(req, tr);
        

        NodeList legendURLs = XPATH.getMatchingNodes(
                getLegendURLXPath("cite:states"), dom); 
                
        assertEquals(1, legendURLs.getLength());
        Element legendURL = (Element) legendURLs.item(0);
        assertTrue(legendURL.hasAttribute("width"));
        assertFalse("20".equals(legendURL.getAttribute("width")));
        assertTrue(legendURL.hasAttribute("height"));
        assertFalse("20".equals(legendURL.getAttribute("height")));
        
        File sampleFile = getSampleFile("cite_states");
        assertTrue(sampleFile.exists());
    }
    
    /**
     * Tests that already cached icons are recreated if related
     * SLD is newer.
     * @throws Exception
     */
    @Test
    public void testCachedLegendURLUpdatedSize() throws Exception {
        GeoServerResourceLoader loader = GeoServerExtensions.bean(GeoServerResourceLoader.class);
        Resource sldResource = loader.get(Paths.path("styles", "Bridges.sld"));
        File sampleFile = getSampleFile("Bridges");
        
        long lastTime = sampleFile.lastModified();
        long lastLength = sampleFile.length();
        long previousTime = sldResource.lastmodified();
        sldResource.file().setLastModified(lastTime + 1000);
        
        // force cleaning of samples cache, to get updates on files
        ((LegendSampleImpl)GeoServerExtensions.bean(LegendSample.class)).reloaded();
        
        TransformerBase tr = createTransformer();
        tr.setIndentation(2);
        Document dom = WMSTestSupport.transform(req, tr);
        
        NodeList legendURLs = XPATH.getMatchingNodes(
                getLegendURLXPath("cite:Bridges"), dom);
        assertEquals(1, legendURLs.getLength());
        Element legendURL = (Element) legendURLs.item(0);
        assertTrue(legendURL.hasAttribute("width"));
        assertEquals("20", legendURL.getAttribute("width"));
        assertTrue(legendURL.hasAttribute("height"));
        assertEquals("20", legendURL.getAttribute("height"));
        assertFalse(getSampleFile("Bridges").length() == lastLength);
        sldResource.file().setLastModified(previousTime);
    }
    
    /**
     * Tests that already cached icons are recreated if related
     * SLD is newer (using Catalog events).
     * @throws Exception
     */
    @Test
    public void testCachedLegendURLUpdatedSize2() throws Exception {
        GeoServerResourceLoader loader = GeoServerExtensions.bean(GeoServerResourceLoader.class);
        Resource sldResource = loader.get(Paths.path("styles", "Bridges.sld"));
        File sampleFile = getSampleFile("Bridges");
        
        long lastTime = sampleFile.lastModified();
        long lastLength = sampleFile.length();
        long previousTime = sldResource.lastmodified();
        sldResource.file().setLastModified(lastTime + 1000);
        
        catalog.firePostModified(catalog.getStyleByName("Bridges"));
        
        TransformerBase tr = createTransformer();
        tr.setIndentation(2);
        Document dom = WMSTestSupport.transform(req, tr);
        
        NodeList legendURLs = XPATH.getMatchingNodes(
                getLegendURLXPath("cite:Bridges"), dom);
        assertEquals(1, legendURLs.getLength());
        Element legendURL = (Element) legendURLs.item(0);
        assertTrue(legendURL.hasAttribute("width"));
        assertEquals("20", legendURL.getAttribute("width"));
        assertTrue(legendURL.hasAttribute("height"));
        assertEquals("20", legendURL.getAttribute("height"));
        assertFalse(getSampleFile("Bridges").length() == lastLength);
        sldResource.file().setLastModified(previousTime);
    }
    
    private String getLegendURLXPath(String layerName) {
        return "/"+ getElementPrefix() + getRootElement() + "/"+ getElementPrefix() + "Capability/"+ getElementPrefix() + "Layer/"+ getElementPrefix() + "Layer["+ getElementPrefix() + "Name/text()='"+layerName+"']/"+ getElementPrefix() + "Style/"+ getElementPrefix() + "LegendURL";
    }

    private void removeFileOrFolder(File file) {
        if (!file.exists()) {
            return;
        }

        if (!file.isDirectory()) {
            file.delete();
        } else {
    
            String[] list = file.list();
            for (int i = 0; i < list.length; i++) {
                removeFileOrFolder(new File(file.getAbsolutePath() + File.separator + list[i]));
            }
    
            file.delete();
        }
        
    }
    
    /**
     * Each WMS version suite of tests has its own TransformerBase implementation.
     *  
     * @return
     */
    protected abstract TransformerBase createTransformer();

    /**
     * Each WMS version has a different root name for the Capabilities XML document.
     *  
     * @return
     */
    protected abstract String getRootElement();
    
    /**
     * Each WMS version uses a different element prefix.
     *  
     * @return
     */
    protected abstract String getElementPrefix();


}
