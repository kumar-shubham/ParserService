package com.pisight.pimoney.beans;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import com.pisight.pimoney.parsers.Parser;

public class ParserFactory {
	
	private static HashMap<String, String> instituteClassMap = new HashMap<>();
	
	private static final ParserFactory PARSER_FACTORY = new ParserFactory();
	
	static{
		instituteClassMap.put("Amex (SG) -Manual", "AMEX");
		instituteClassMap.put("Citibank (SG) -Manual", "CITI");
		instituteClassMap.put("DBS (SG) -Manual", "DBS");
		instituteClassMap.put("ANZ (SG) -Manual", "ANZ");
		instituteClassMap.put("SCB (SG) -Manual", "SCB");
		instituteClassMap.put("OCBC (SG) -Manual", "OCBC");
		instituteClassMap.put("ICICI (SG) -Manual", "ICICI");
		instituteClassMap.put("UOB (SG) -Manual", "UOB");
	}
	
	public static ParserFactory getFactory(){
		return PARSER_FACTORY;
	}

	public Parser getParser(String name, String container, String locale, String type) throws ClassNotFoundException, NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		
		if(container.equalsIgnoreCase("card") || container.equalsIgnoreCase("CreditCards")){
			container = "Card";
		}
		else if(container.equalsIgnoreCase("bank") || container.equalsIgnoreCase("Banks")){
			container = "Bank";
		}
		
		
		String className = "com.pisight.pimoney.parsers." + instituteClassMap.get(name) + locale.toUpperCase() + container + type.toUpperCase() + "Scrapper";
		Class<?> cls = Class.forName(className);
		Object object = cls.newInstance();
		Parser parser = (Parser)object;
		return parser;
	}

}
