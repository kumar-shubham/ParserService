package com.pisight.pimoney.parsers;

import java.io.File;

import org.openqa.selenium.WebDriver;

import com.pisight.pimoney.beans.Response;

public interface Parser {

	public Response parse(WebDriver driver, File file) throws Exception;

}
