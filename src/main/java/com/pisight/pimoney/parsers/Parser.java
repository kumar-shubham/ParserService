package com.pisight.pimoney.parsers;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.openqa.selenium.WebDriver;

import com.pisight.pimoney.beans.Response;

public interface Parser {

	public Response parse(WebDriver driver, PDDocument pdDocument) throws Exception;

}
