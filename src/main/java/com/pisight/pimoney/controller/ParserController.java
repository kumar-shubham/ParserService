package com.pisight.pimoney.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Base64;
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


	@RequestMapping(value = "/secretParse", method = RequestMethod.POST, headers = "Accept=application/json")
	public Response ParseStatement(@RequestBody DocumentRequest doc) throws Exception {

		String name = doc.getName();
		String container = doc.getContainer();
		String locale = doc.getLocale();
		String type = doc.getType();
		
		
		ParserFactory pf = ParserFactory.getFactory();
		Parser p = null;
		try{
			p = pf.getParser(name, container, locale, type);
		}catch(ClassNotFoundException e){
			System.out.println("Parser not found for this site. Please check the request parameters");
			System.err.println(e.getMessage());
			return null;
		}
		
		
		System.out.println("PPPPPPPPPPPPPPPPPPPPPPPP:::::::::: " + doc.getDocByte());
		
		String docByte = doc.getDocByte();
		byte[] decodeByte = Base64.decodeBase64(docByte);
		File file  = new File("temp.pdf");
		FileOutputStream fos = new FileOutputStream(file);
		fos.write(decodeByte);
		
		
		WebDriver driver = getDriver();
		Response response = p.parse(driver, file);
		fos.close();

		System.out.println("KKKKKKKKKKKKKKKKK  ::: " + response);
		return response;
	}

	private WebDriver getDriver() {
		// TODO Auto-generated method stub
		Path p1 = Paths.get(System.getProperty("user.home"), "drivers", "phantomjs");

		DesiredCapabilities caps = new DesiredCapabilities();
		caps.setJavascriptEnabled(true);  
		caps.setCapability(PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY, p1.toString());

		WebDriver driver = new PhantomJSDriver(caps);

		driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

		return driver;
	}

}
