package com.pisight.pimoney.parsers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import com.pisight.pimoney.beans.CardAccount;
import com.pisight.pimoney.beans.CardTransaction;
import com.pisight.pimoney.beans.ParserUtility;
import com.pisight.pimoney.beans.Response;

public class OCBCSGCardPDFScrapper extends PDFParser {

	@Override
	public Response parse(WebDriver driver, File file) throws Exception {
		// TODO Auto-generated method stub
		String page = parsePDFToHTML(file);

		JavascriptExecutor js = (JavascriptExecutor) driver;

		System.out.println(page);

		js.executeScript(page);

		return scrapeStatement(driver);
	}

	private Response scrapeStatement(WebDriver driver) throws Exception {
		// TODO Auto-generated method stub
		
		Response response = new Response();
		System.out.println("#@#@#@#@##@#@##@#@#@##@#@#@#@#@##@#@#@#@#@#@##@#@#@#@#");
		System.out.println();
		WebElement page = driver.findElement(By.id("PDF_TO_HTML"));
		
		WebElement holderEle = page.findElement(By.xpath("//td[contains(text(),'NAME : ')]"));
		
		String holderText = holderEle.getText().trim();
		String accountHolder = holderText.substring(holderText.indexOf(":")+1, holderText.indexOf("STATEMENT DATE")).trim();
		
		WebElement detailEle = page.findElement(By.xpath("//td[contains(text(), 'STATEMENT DATE PAYMENT DUE DATE')]/../following-sibling::tr[1]"));
		
		String detailText = detailEle.getText().trim();
		
		String statementDate = detailText.substring(0, detailText.indexOf(" ")).trim();
		
		String dueDate = detailText.substring(detailText.indexOf(" ")+1, detailText.indexOf(" ", detailText.indexOf(" ")+1)).trim();
		
		String  creditLimit = detailText.substring(detailText.indexOf("$"), detailText.indexOf(" ", detailText.indexOf("$"))).trim();
		
		String availableCredit = detailText.substring(detailText.indexOf("$", detailText.indexOf("$")+1), detailText.lastIndexOf(" "));
		
		
		creditLimit = creditLimit.replace("$", "");
		creditLimit = creditLimit.replaceAll(",", "");
		
		availableCredit = availableCredit.replace("$", "");
		availableCredit = availableCredit.replaceAll(",", "");
		
		System.out.println("Statement Date          ::: " + statementDate);
		System.out.println("Due Date                ::: " + dueDate);
		System.out.println("Credit Limit            ::: " + creditLimit);
		System.out.println("Availabe Credit         ::: " + availableCredit);
		
		List<WebElement> accountEle = page.findElements(By.xpath("//td[contains(text(), ' PAYMENT AMOUNT')]/../following-sibling::tr"));
		
		String accountRegEx = "(\\d{4}-\\d{4}-\\d{4}-\\d{4}).* ((\\d*,)?\\d+(.)\\d+) ((\\d*,)?\\d+(.)\\d+) .*";
		
		String accountendRegEx = "TOTAL( (\\d*,)?\\d+(.)\\d+){2}.*";
		
		Pattern pAccount = Pattern.compile(accountRegEx);
		Pattern pAccountEnd = Pattern.compile(accountendRegEx);
		
		List<CardAccount> accounts  = new ArrayList<CardAccount>();
		
		boolean accountsFound = false;
		for(WebElement rowEle: accountEle){
			
			String rowText = rowEle.getText().trim();
			
			Matcher m = pAccount.matcher(rowText);
			
			if(m.matches()){
				if(!accountsFound){
					accountsFound = true;
				}
				String accountNumber = m.group(1);
				
				String amountDue = m.group(2);
				
				String minPayment = m.group(5);
				
				CardAccount ca = new CardAccount();
				
				ca.setAccountNumber(accountNumber);
				ca.setAmountDue(amountDue);
				ca.setMinAmountDue(minPayment);
				ca.setBillDate(statementDate);
				ca.setDueDate(dueDate);
				ca.setAvailableCredit(availableCredit);
				ca.setTotalLimit(creditLimit);
				ca.setAccountHolder(accountHolder);
				accounts.add(ca);
				response.addCardAccount(ca);
				
			}
			else{
				if(accountsFound){
					
					m = pAccountEnd.matcher(rowText);
					
					if(m.matches()){
						System.out.println("All accounts scrapped. Now exiting the loop.");
						break;
						
					}
				}
			}
			
		}
		
		String transRegEx = "(\\d{2}/\\d{2}) (.*) ((\\d*,)?\\d+(.)\\d+)(.*)$";
		String transEndRegEx = "TOTAL (\\d*,)?\\d+(.)\\d+";
		
		Pattern pTrans = Pattern.compile(transRegEx);
		Pattern pTransEnd = Pattern.compile(transEndRegEx);
		
		
		
		for(CardAccount account:accounts){
			
			System.out.println("inside for");
			List<WebElement> transEle = page.findElements(By.xpath("//td[contains(text(), ' " + account.getAccountNumber() + "')]/../following-sibling::tr"));
			boolean transFound = false;
			for(WebElement rowEle:transEle){
				System.out.println("inside for for");
				String rowText = rowEle.getText().trim();
				
				Matcher m = pTrans.matcher(rowText);
				
				if(m.matches()){
					
					System.out.println("inside for for if");
					if(!transFound){
						transFound = true;
					}
					
					String transDate = m.group(1);
					
					String desc = m.group(2);
					
					String amount = m.group(3);
					String transType = null;
					
					if("CR".equalsIgnoreCase(m.group(6))){
						transType = CardTransaction.TRANSACTION_TYPE_CREDIT;
					}
					else{
						transType = CardTransaction.TRANSACTION_TYPE_DEBIT;
					}
					
					transDate = ParserUtility.getYear(transDate, ParserUtility.DATEFORMAT_DD_SLASH_MM, 
							account.getBillDate(), ParserUtility.DATEFORMAT_DD_DASH_MM_DASH_YYYY);
					CardTransaction ct = new CardTransaction();
					
					ct.setAccountNumber(account.getAccountNumber());
					ct.setAmount(amount);
					ct.setDescription(desc);
					ct.setTransDate(transDate);
					ct.setTransactionType(transType);
					account.addTransaction(ct);
					
				}
				else{
					
					if(transFound){
						m = pTransEnd.matcher(rowText);
						
						if(m.matches()){
							System.out.println("End of transactions for the account " + account.getAccountNumber());
							break;
						}
					
					}
					
					
				}
				
			}
			
		}
		return response;
	}

}
