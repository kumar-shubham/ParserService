package com.pisight.pimoney.parsers;

import java.io.File;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.pisight.pimoney.beans.PDFExtracter;


public abstract class PDFParser implements Parser {
	
	protected String parsePDFToHTML(File file) throws Exception{
		
		PDFExtracter pdfExtractor = new PDFExtracter(file);
		
		String page = pdfExtractor.convertPDFToHTML(" ");
		
		return page;
		
	}
	
	protected String parsePDFToHTML(PDDocument pdDocument) throws Exception{
		
		PDFExtracter pdfExtractor = new PDFExtracter(pdDocument);
		
		String page = pdfExtractor.convertPDFToHTML(" ");
		
		return page;
		
	}
	
	protected String parsePDFToHTML(PDDocument pdDocument, String regex, String startText, String endText, String markerText, String haltText) throws Exception{
		
		PDFExtracter pdfExtractor = new PDFExtracter(pdDocument);
		
		String page = pdfExtractor.convertPDFToHTML(" ", regex, startText, endText, markerText, haltText);
		
		return page;
		
	}
	
	protected String parsePDFToHTML(PDDocument pdDocument, String regex, String startText, String endText, String markerText, String haltText, String prevText) throws Exception{
		
		PDFExtracter pdfExtractor = new PDFExtracter(pdDocument);
		
		String page = pdfExtractor.convertPDFToHTML(" ", regex, startText, endText, markerText, haltText, prevText);
		
		return page;
		
	}

}
