package com.pisight.pimoney.parsers;

import java.io.File;

import com.pisight.pimoney.beans.PDFExtracter;


public abstract class PDFParser implements Parser {
	
	protected String parsePDFToHTML(File file) throws Exception{
		
		PDFExtracter pdfExtractor = new PDFExtracter(file);
		
		String page = pdfExtractor.convertPDFToHTML(" ");
		
		return page;
		
	}

}
