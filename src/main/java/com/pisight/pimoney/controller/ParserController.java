package com.pisight.pimoney.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;
import org.apache.pdfbox.exceptions.CryptographyException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.StandardDecryptionMaterial;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.pisight.pimoney.beans.DocumentRequest;
import com.pisight.pimoney.beans.ParserFactory;
import com.pisight.pimoney.beans.Response;
import com.pisight.pimoney.parsers.Parser;

@RestController
public class ParserController {
	
	private static final Logger LOGGER = Logger.getLogger( ParserController.class.getName() );

	@RequestMapping(value = "/checkencryption", method = RequestMethod.POST)
	public boolean checkEncryption(@RequestBody DocumentRequest doc) throws Exception{

		String docByte = doc.getDocByte();
		byte[] decodeByte = Base64.decodeBase64(docByte);
		Path filepath = Paths.get(System.getProperty("user.dir"), "public", "temp1.pdf");
		File file  = filepath.toFile();
		FileOutputStream fos = new FileOutputStream(file);
		fos.write(decodeByte);
		if(null == file || !file.exists()){
			throw new Exception("File does not exist");
		}
		PDDocument pdDocument = PDDocument.load(file);
		if(pdDocument.isEncrypted()){
			pdDocument.setAllSecurityToBeRemoved(true);
			try{
				StandardDecryptionMaterial sdm = new StandardDecryptionMaterial("");
				pdDocument.openProtection(sdm);
			}catch(CryptographyException e){
				if(e.getMessage().contains("The supplied password does not match")){
					LOGGER.info("Password protected pdf detected");
					Response r = new Response();
					r.setEncrypted(true);
					r.setPswdCorrect(false);
					fos.close();
					return true;
				}
			}
		}
		fos.close();
		return false;
	}


	@RequestMapping(value = "/parse", method = RequestMethod.POST)
	public Response parseStatement(@RequestBody DocumentRequest doc) throws Exception {

		String name = doc.getName();
		String container = doc.getContainer();
		String locale = doc.getLocale();
		String type = doc.getType();
		ParserFactory pf = ParserFactory.getFactory();
		Parser p = null;
		try{
			p = pf.getParser(name, container, locale, type);
		}catch(ClassNotFoundException e){
			LOGGER.log(Level.WARNING, "Parser not found for this site. Please check the request parameters");
			System.err.println(e.getMessage());
			return null;
		}


//		LOGGER.info("PPPPPPPPPPPPPPPPPPPPPPPP:::::::::: " + doc.getDocByte());

		LOGGER.info("current directory :: " + System.getProperty("user.dir"));
		Path currentRelativePath = Paths.get("");
		String s = currentRelativePath.toAbsolutePath().toString();
		LOGGER.info("Current relative path is: " + s);
		String docByte = doc.getDocByte();
		byte[] decodeByte = Base64.decodeBase64(docByte);
		Path filepath = Paths.get(System.getProperty("user.dir"), "public", "temp.pdf");
		File file  = filepath.toFile();
		FileOutputStream fos = new FileOutputStream(file);
		fos.write(decodeByte);

		if(null == file || !file.exists()){
			throw new Exception("File doesn't exist");
		}
		PDDocument pdDocument = PDDocument.load(file);
		if(pdDocument.isEncrypted()){
			String temp = null;
			if(doc.getPswd() == null){
				temp = "";
			}
			else{
				temp = new String(doc.getPswd());
			}
			try{
				StandardDecryptionMaterial sdm = new StandardDecryptionMaterial(temp);
				pdDocument.openProtection(sdm);
			}catch(CryptographyException e){
				if(e.getMessage().contains("The supplied password does not match")){
					LOGGER.info("Incorrect password");
					Response r = new Response();
					r.setEncrypted(true);
					r.setPswdCorrect(false);
					fos.close();
					return r;
				}
			}
			
		}

		WebDriver driver = getDriver();
		Response response = null;
		try{
			LOGGER.info(" starting parsing of the document -@@@@@@@");
			response = p.parse(driver, pdDocument);
			LOGGER.info(" parsing of the document done -@@@@@@@");
		}finally{
			LOGGER.info("xxXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXxxx~~~closing the resoureses~~~XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXxx");
			fos.close();
			driver.quit();
		}
		LOGGER.info("KKKKKKKKKKKKKKKKK  ::: " + response);
		return response;
	}

	private WebDriver getDriver() {
		// TODO Auto-generated method stub
		Path p1 = Paths.get(System.getProperty("user.home"), "drivers", "phantomjs");

		DesiredCapabilities caps = new DesiredCapabilities();
		caps.setJavascriptEnabled(true);  
		caps.setCapability(PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY, p1.toString());

		WebDriver driver = new PhantomJSDriver(caps);

		driver.manage().timeouts().implicitlyWait(2, TimeUnit.SECONDS);

		return driver;
	}

}
