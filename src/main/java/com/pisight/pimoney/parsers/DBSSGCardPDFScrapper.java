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

import com.pisight.pimoney.beans.Response;
import com.pisight.pimoney.beans.CardAccount;
import com.pisight.pimoney.beans.CardTransaction;
import com.pisight.pimoney.beans.ParserUtility;

public class DBSSGCardPDFScrapper extends PDFParser {

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
		
		WebElement holderEle = page.findElement(By.xpath("//td[text() = 'DBS Bank Ltd']/../following-sibling::tr[4]"));
		
		String accountHolder = holderEle.getText().trim();
		
		WebElement detailEle = page.findElement(By.xpath("//td[contains(text(), 'STATEMENT DATE CREDIT LIMIT')]/../following-sibling::tr[1]"));
		
		String detailText  = detailEle.getText().trim();
		
		String statementDate = detailText.substring(0, 11).trim();
		
		String creditLimit = detailText.substring(12, detailText.lastIndexOf("$")).trim();
		creditLimit = creditLimit.replace(",", "").trim();
		creditLimit = creditLimit.replace("$", "").trim();
		
		String dueDate = detailText.substring(detailText.length()-11).trim();
		
		
		List<CardAccount> accounts  = new ArrayList<CardAccount>();
		
		List<WebElement> accountEle = page.findElements(By.xpath("//td[contains(text(), 'BALANCE PAYMENT AMOUNT FOR EACH A/C')]/../following-sibling::tr"));
		
		String accountRegex = "(\\w*\\s*)+( \\$ (\\d*,)?\\d+(.)\\d+){2}( \\d{4}){4}";
		String endRegEx = "TOTAL( \\$ (\\d*,)?\\d+(.)\\d+){2}.*";

	    Pattern rAcc = Pattern.compile(accountRegex);
	    Pattern rEnd = Pattern.compile(endRegEx);
		boolean accountFound = false;
		for(WebElement rowEle: accountEle){
			
			String rowText = rowEle.getText().trim();
			
			Matcher m = rAcc.matcher(rowText);
			if(m.matches()){
				if(!accountFound){
					accountFound =  true;
				}
				
				String accountNumber = rowText.substring(rowText.length() - 19).trim();
				rowText = rowText.replace(accountNumber, "").trim();
				
				String minPayment = rowText.substring(rowText.lastIndexOf("$")).trim();
				
				String amountDue = rowText.substring(rowText.indexOf("$"),rowText.lastIndexOf("$")).trim();
				rowText = rowText.replace(minPayment, "").trim();
				rowText = rowText.replace(amountDue, "").trim();
				
				String accountName = rowText;
				
				minPayment = minPayment.replace("$", "").trim();
				amountDue = amountDue.replace("$", "").trim();
				amountDue = amountDue.replace(",", "");
				String availableCredit = String.format("%.2f", (Double.parseDouble(creditLimit) - Double.parseDouble(amountDue)));
				
				
				System.out.println();
				System.out.println("Account Number   ::: " + accountNumber);
				System.out.println("Account Name     ::: " + accountName);
				System.out.println("Account Holder   ::: " + accountHolder);
				System.out.println("Minimum Payment  ::: " + minPayment);
				System.out.println("Amount Due       ::: " + amountDue);
				System.out.println("Credit Limit     ::: " + creditLimit);
				System.out.println("Available Credit ::: " + availableCredit);
				System.out.println("Bill Date        ::: " + statementDate);
				System.out.println("Due Date         ::: " + dueDate);
				System.out.println();
				CardAccount ca = new CardAccount();
				ca.setAccountName(accountName);
				ca.setAccountNumber(accountNumber);
				ca.setMinAmountDue(minPayment);
				ca.setAmountDue(amountDue);
				ca.setTotalLimit(creditLimit);
				ca.setBillDate(statementDate);
				ca.setAvailableCredit(availableCredit);
				ca.setDueDate(dueDate);
				ca.setAccountHolder(accountHolder);
				accounts.add(ca);
				response.addCardAccount(ca);
				
				
			}
			else{
				
				if(accountFound){
					m = rEnd.matcher(rowText);
					if(m.matches()){
						System.out.println("No more accounts to scrape so exiting the loop");
						break;
					}
				}
				
			}
		
		}
		
		for(CardAccount account: accounts){
			
			String identifier = account.getAccountName().trim() + " CARD NO.: " + account.getAccountNumber();
			
			List<WebElement> transEle = page.findElements(By.xpath("//td[contains(text(), '" + identifier + "')]/../following-sibling::tr"));
			
			String transRegEx = "\\d{2} \\w{3} .* (\\d*,)?\\d+(.)\\d+( CR)?";
			String transEndRegEx = "TOTAL: (\\d*,)?\\d+(.)\\d+";
			
			Pattern pTrans = Pattern.compile(transRegEx);
			Pattern pTransEnd = Pattern.compile(transEndRegEx);
			
			boolean transFound = false;
			for(WebElement rowEle:transEle){
				
				String rowText = rowEle.getText().trim();
				
				Matcher m = pTrans.matcher(rowText);
				
				if(m.matches()){
					
					if(!transFound){
						transFound = true;
					}
					
					String temp = rowText.substring(rowText.lastIndexOf(" ")).trim();
					String transType = null;
					String amount = null;
					String transDate = "";
					String desc = "";
					
					if(temp.equalsIgnoreCase("CR")){
						transType = CardTransaction.TRANSACTION_TYPE_CREDIT;
						rowText = rowText.replace(temp, "").trim();
						
						temp = rowText.substring(rowText.lastIndexOf(" ")).trim();
					}
					else{
						transType = CardTransaction.TRANSACTION_TYPE_DEBIT;
					}
					
					amount = temp;
					rowText = rowText.replace(temp, "").trim();
					transDate = rowText.substring(0, 6).trim();
					desc = rowText.substring(6).trim();
					
					System.out.println();
					System.out.println();
					System.out.println("Transaction Date   ::: " + transDate);
					System.out.println("Transaction Amount ::: " + amount);
					System.out.println("Transaction Type   ::: " + transType);
					System.out.println("Transaction Desc   ::: " + desc);
					System.out.println();
					
					CardTransaction ct = new CardTransaction();
					
					transDate = ParserUtility.getYear(transDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM, 
							account.getBillDate(), ParserUtility.DATEFORMAT_DD_SPACE_MMM_SPACE_YYYY);
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
							System.out.println("End of transaction for the account " + account.getAccountName());
							break;
						}
					
					}
					
					
				}
				
			}
			
		}
		
		return response;
	}

}
