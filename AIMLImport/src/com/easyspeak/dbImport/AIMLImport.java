package com.easyspeak.dbImport;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

public class AIMLImport {
	public static String cleanInsertString(String string) {
		String s = "\"" + string.replace("\\", "\\\\").replace("\"", "\"\"") + "\"";
		
		if (s.length() > 8000) {
			System.out.println("Too Long: " + string);
			s = "";
		}
		
		return s;
	}

	public static String readFile(Path path) throws IOException 
	{
		return readFile(path, Charset.forName("UTF-8"));
	}
	
	public static String readFile(Path path, Charset encoding) throws IOException 
	{
		byte[] encoded = Files.readAllBytes(path);
		return new String(encoded, encoding);
	}
	
	public static Node parseFile(String fileName) throws Exception {
		File file = new File(fileName);

		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(file);
		doc.getDocumentElement().normalize();
		Node root = doc.getDocumentElement();
		return root;
	}
	
	public static String innerXml(Node node) {
	    DOMImplementationLS lsImpl = (DOMImplementationLS)node.getOwnerDocument().getImplementation().getFeature("LS", "3.0");
	    LSSerializer lsSerializer = lsImpl.createLSSerializer();
	    DOMConfiguration config = lsSerializer.getDomConfig();
	    config.setParameter("xml-declaration", false);
	    NodeList childNodes = node.getChildNodes();
	    StringBuilder sb = new StringBuilder();
	    for (int i = 0; i < childNodes.getLength(); i++) {
	    	sb.append(lsSerializer.writeToString(childNodes.item(i)));
	    }
	    return sb.toString(); 
	}
	
	public static void aiml(Connection dest, String botPath, int userId) throws Exception {
		File folder = new File(botPath, "aiml");
		XPath xpath = XPathFactory.newInstance().newXPath();
		
		// remove old user items...
		PreparedStatement clear = dest.prepareStatement("delete from utterances where userid = ?");
		clear.setInt(1, userId);
		clear.execute();
		clear = dest.prepareStatement("delete from responses where userid = ?");
		clear.setInt(1, userId);
		clear.execute();

		StringBuilder sb = null;
		int count = 0;
		boolean firstStatement = true;
		
		ArrayList<Long> utteranceIds = new ArrayList<>();

		// insert utterances
		for (File file : folder.listFiles()) {
			if (file.getName().endsWith("aiml") && !file.getName().startsWith(".")) {
				
				System.out.println("Importing utterances from " + file.getName());
				
				NodeList categories = (NodeList) xpath.evaluate("/aiml/category", new InputSource(new FileReader(file.getPath())), XPathConstants.NODESET);
								
				for (int i = 0; i < categories.getLength(); i++) {
					Node category = categories.item(i);
					
					Node patternNode = (Node)xpath.evaluate("pattern", category, XPathConstants.NODE);
					
					if (patternNode == null) {
						System.out.println("Didn't find pattern " + category.toString());
						System.exit(-1);
					}
					
					String pattern = cleanInsertString(innerXml(patternNode));
					
					if (pattern.equals("\"*\"")) {
						continue;
					}
					
					if (firstStatement) {
						sb = new StringBuilder();
						sb.append("INSERT INTO utterances (userid, utterance) VALUES (");
						firstStatement = false;
					}
					else {
						sb.append(",(");
					}
					
					sb.append(userId);
					sb.append(",");
					sb.append(pattern);
					sb.append(")");
					
					if ((++count % 30000 == 0) || sb.length() > 8000000) {
						PreparedStatement insert = dest.prepareStatement(sb.toString(), Statement.RETURN_GENERATED_KEYS);
						insert.execute();
				        ResultSet generatedKeys = insert.getGeneratedKeys();
				        while (generatedKeys.next()) {
				        	utteranceIds.add(generatedKeys.getLong(1));
				        }
						insert.close();
						firstStatement = true;
						System.out.println(count);
						count = 0;
					}
				}
			}
		}
				
		if (!firstStatement) {
			PreparedStatement insert = dest.prepareStatement(sb.toString(), Statement.RETURN_GENERATED_KEYS);
			insert.execute();
	        ResultSet generatedKeys = insert.getGeneratedKeys();
	        while (generatedKeys.next()) {
	        	utteranceIds.add(generatedKeys.getLong(1));
	        }
	        insert.close();
			firstStatement = true;
			System.out.println(count);
			count = 0;
		}			
		
		Iterator<Long> utteranceIterator = utteranceIds.iterator();

		// insert responses
		for (File file : folder.listFiles()) {
			if (file.getName().endsWith("aiml") && !file.getName().startsWith(".")) {
				System.out.println("Importing responses from " + file.getName());
				
				NodeList categories = (NodeList) xpath.evaluate("/aiml/category", new InputSource(new FileReader(file.getPath())), XPathConstants.NODESET);
								
				for (int i = 0; i < categories.getLength(); i++) {
					Node category = categories.item(i);
					
					Node patternNode = (Node)xpath.evaluate("pattern", category, XPathConstants.NODE);
					String pattern = cleanInsertString(innerXml(patternNode));
					
					if (pattern.equals("\"*\"")) {
						continue;
					}
					
					Long utteranceId = utteranceIterator.next();					
					Element templateNode = (Element)xpath.evaluate("template", category, XPathConstants.NODE);
					
					if (templateNode == null) {
						System.out.println("Didn't find template " + category.toString());
						System.exit(-1);
					}
					
					ArrayList<String> responses = new ArrayList<>();

					NodeList responseNodes = (NodeList)xpath.evaluate("random/li", templateNode, XPathConstants.NODESET);
					
					if (responseNodes == null || responseNodes.getLength() == 0) {
						// not a random ...
						responses.add(innerXml(templateNode));
					}
					else {
						for (int j = 0; j < responseNodes.getLength(); j++) {
							responses.add(innerXml(responseNodes.item(j)));
						}
					}
					
			        for (String response : responses) {
						if (firstStatement) {
							sb = new StringBuilder();
							sb.append("INSERT INTO responses (userId, utteranceId, response) VALUES (");
							firstStatement = false;
						}
						else {
							sb.append(",(");
						}

						sb.append(userId);
						sb.append(",");
						sb.append(utteranceId);
						sb.append(",");
						sb.append(cleanInsertString(response));
						sb.append(")");

						if ((++count % 30000 == 0) || sb.length() > 8000000) {
							Statement ins = dest.createStatement();
							ins.execute(sb.toString());
							ins.close();
							firstStatement = true;
							System.out.println(count);
							count = 0;
						}
					}
				}
			}
		}
				
		if (!firstStatement) {
			Statement ins = dest.createStatement();
			ins.execute(sb.toString());
			ins.close();
			System.out.println(count);
		}			
	}
				
	public static void main(String[] args) {
		String botPath = "/Users/mike/Development/eyespeak/server/WebContent/bots/alice2";
		int userId = 0;

		//String botPath = "/Users/mike/Downloads/program-ab-0.0.6.26/bots/eliza";
		//int userId = 1;
		
		try {			
			Class.forName("com.mysql.jdbc.Driver");
			Connection dest = DriverManager.getConnection("jdbc:mysql://tecartadb.czrgu6ly1kp6.us-east-1.rds.amazonaws.com:3306/eyespeak?useUnicode=true", "tdcadmin", "Secur1ty");

			aiml(dest, botPath, userId);

			dest.close();
		}
		catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
	}
}
