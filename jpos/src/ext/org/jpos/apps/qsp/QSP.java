package org.jpos.apps.qsp;

import java.io.File;
import java.io.IOException;
import org.apache.xerces.parsers.*;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.ErrorHandler;

import org.jpos.util.SimpleLogListener;
import org.jpos.util.SystemMonitor;
import org.jpos.util.Logger;
import org.jpos.util.LogEvent;
import org.jpos.util.LogSource;
import org.jpos.core.ConfigurationException;

/**
 * @author <a href="mailto:apr@cs.com.uy">Alejandro P. Revilla</a>
 * @version $Revision$ $Date$
 * @see <a href="http://www.cebik.com/qsig.html">QSP</a>
 */
public class QSP implements ErrorHandler, LogSource {
    Document config;
    Logger logger;
    String realm;
    File configFile;
    long lastModified;
    static ControlPanel controlPanel = null;
    long monitorConfigInterval = 60 * 1000;

    public static String[] SUPPORTED_TAGS = 
	{ "logger",
	  "qsp-config",
	  "log-listener",
	  "persistent-engine",
	  "sequencer",
	  "control-panel",
	  "channel",
	  "filter",
	  "mux",
	  "server",
	  "request-listener",
	  "card-agent",
	  "task" 
	};

    public QSP () {
	super();
	// setLogger (new Logger(), "qsp");
	// logger.setName ("qsp");
    }
    public void setConfig (Document config) {
	this.config = config;
    }
    public void setConfigFile (File f) {
	this.configFile = f;
	this.lastModified = f.lastModified();
    }
    public void setMonitorConfigInterval (long l) {
	monitorConfigInterval = l;
    }
    public File getConfigFile () {
	return configFile;
    }
    public ControlPanel initControlPanel (int rows, int cols) {
	if (controlPanel == null) {
	    synchronized (QSP.class) {
		if (controlPanel == null) 
		    controlPanel = new ControlPanel (this, rows, cols);
	    }
	}
	return controlPanel;
    }
    public ControlPanel getControlPanel (){
	return controlPanel;
    }
    public void warning (SAXParseException e) throws SAXException {
	Logger.log (new LogEvent (this, "warning", e));
	throw e;
    }
    public void error (SAXParseException e) throws SAXException {
	Logger.log (new LogEvent (this, "error", e));
	throw e;
    }

    public void fatalError (SAXParseException e) throws SAXException {
	Logger.log (new LogEvent (this, "fatalError", e));
	throw e;
    }
    public void setLogger (Logger logger, String realm) {
	this.logger = logger;
	this.realm  = realm;
    }
    public String getRealm () {
	return realm;
    }
    public Logger getLogger () {
	return logger;
    }
    public void configure (String tagname) throws ConfigurationException {
	QSPConfigurator configurator = QSPConfiguratorFactory.create (tagname);
	NodeList nodes = config.getElementsByTagName (tagname);
	for (int i=0; i<nodes.getLength(); i++) 
	    configurator.config (this, nodes.item(i));
    }
    public void reconfigure (String tagname) throws ConfigurationException {
	QSPConfigurator configurator = QSPConfiguratorFactory.create (tagname);
	if (configurator instanceof QSPReConfigurator) {
	    NodeList nodes = config.getElementsByTagName (tagname);
	    for (int i=0; i<nodes.getLength(); i++) 
		((QSPReConfigurator)configurator).reconfig 
		    (this, nodes.item(i));
	}
    }
    private boolean monitorConfigFile () {
	long l;
	while (lastModified == (l=configFile.lastModified()))
	    try {
		Thread.sleep (monitorConfigInterval);
	    } catch (InterruptedException e) { }
	lastModified = l;
	return true;
    }
    public static void main (String args[]) {
	if (args.length != 1) {
	    System.out.println ("Usage: org.jpos.apps.qsp.QSP <configfile>");
	    System.exit (1);
	}
	DOMParser parser = new DOMParser();
	QSP qsp = new QSP();
	// qsp.getLogger().addListener (new SimpleLogListener(System.out));
	try {
	    qsp.setConfigFile (new File (args[0]));
	    parser.setFeature("http://xml.org/sax/features/validation", true);
	    parser.setErrorHandler (qsp);
	    parser.parse (qsp.getConfigFile().getPath());
	    qsp.setConfig (parser.getDocument());
	    for (int i=0; i<SUPPORTED_TAGS.length; i++)
		qsp.configure (SUPPORTED_TAGS[i]);

	    if (controlPanel != null)
		controlPanel.showUp();

	    if (qsp.getLogger() != null) 
		new SystemMonitor (3600000, qsp.getLogger(), "monitor");
		    
	    while (qsp.monitorConfigFile ()) {
		parser.parse (qsp.getConfigFile().getPath());
		qsp.setConfig (parser.getDocument());
		for (int i=0; i<SUPPORTED_TAGS.length; i++)
		    qsp.reconfigure (SUPPORTED_TAGS[i]);
	    }
	} catch (IOException e) {
	    Logger.log (new LogEvent (qsp, "error", e));
	    System.out.println (e);
	} catch (SAXException e) {
	    Logger.log (new LogEvent (qsp, "error", e));
	    System.out.println (e);
	} catch (ConfigurationException e) {
	    Logger.log (new LogEvent (qsp, "error", e));
	    System.out.println (e);
	}
    }
}
