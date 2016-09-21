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
import com.pisight.pimoney.beans.ParserUtility;
import com.pisight.pimoney.beans.BankAccount;
import com.pisight.pimoney.beans.BankTransaction;

public class UOBSGBankPDFScrapper extends PDFParser {

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
		
		WebElement holderEle = page.findElement(By.tagName("td"));
		String holderName =  holderEle.getText().trim();
		
		holderName = holderName.replace("Contact Us", "");
		
		WebElement stmtdateEle = page.findElement(By.xpath("//td[contains(text(), 'Account Overview as at ')]"));
		
		String stmtDate = stmtdateEle.getText().trim();
		
		stmtDate = stmtDate.replace("Account Overview as at", "").trim();
		
		
		System.out.println("Account Holder   ::: " + holderName);
		
		List<BankAccount> accounts  = new ArrayList<BankAccount>();
		
		List<WebElement> accountEle = page.findElements(By.xpath("//td[contains(text(), 'Currency Credit Line Interest Earned')]/../following-sibling::tr"));
		
		String accountRegEx = "(.*) (\\w{3}) ((\\d*,)?\\d+(.)\\d+) ((\\d*,)?\\d+(.)\\d+) (.*) (-?(\\d*,)?\\d+(.)\\d+)";
		String accountNumberRegEx = "(\\d{3}-\\d{3}-\\d{3}-\\d)";
		String accountEndRegEx = "Total .* (\\d*,)?\\d+(.)\\d+";
		
		Pattern pAccount = Pattern.compile(accountRegEx);
		Pattern pAccountEnd = Pattern.compile(accountEndRegEx);
		Pattern pAccNumber = Pattern.compile(accountNumberRegEx);
		
		boolean accountsFound = false;
		BankAccount temp = null;
		for(WebElement rowEle: accountEle){
			System.out.println("1");
			String rowText = rowEle.getText().trim();
			
			Matcher m = pAccount.matcher(rowText);
			
			if(m.matches()){
				System.out.println("2");
				
				if(!accountsFound){
					System.out.println("3");
					accountsFound = true;
				}
				
				String accountName = m.group(1).trim();
				String currency = m.group(2).trim();
				String balance = m.group(10);
				
				BankAccount ba = new BankAccount();
				
				ba.setAccountHolder(holderName);
				ba.setAccountBalance(balance);
				ba.setAccountName(accountName);
				ba.setCurrency(currency);
				ba.setBillDate(stmtDate);
				temp = ba;
				accounts.add(ba);
				response.addBankAccount(ba);
			}
			else{
				System.out.println("4");
				
				if(accountsFound){
					
					m = pAccNumber.matcher(rowText);
					
					if(m.matches()){
						String accountNumber = m.group(1);
						if(temp != null){
							temp.setAccountNumber(accountNumber);
							
							System.out.println();
							System.out.println("Account Balance      ::: " + temp.getAccountBalance());
							System.out.println("Account Currency     ::: " + temp.getCurrency());
							System.out.println("Account Number       ::: " + accountNumber);
							System.out.println("Account Name         ::: " + temp.getAccountName());
							System.out.println("Account Holder       ::: " + temp.getAccountHolder());
							System.out.println();
						}
					}
					else{
						System.out.println("5");
						m = pAccountEnd.matcher(rowText);
						
						if(m.matches()){
							System.out.println("All accounts scrapped. Now Skipping the loop.");
							break;
						}
					}
				}
			}
			
		}
		
		for(BankAccount account:accounts){
			
			String identifier = account.getAccountName() + " " + account.getAccountNumber();
			
			System.out.println("identifier  ::: " + identifier);
			
			List<WebElement> transEle = page.findElements(By.xpath("//td[contains(text(), '" + account.getAccountNumber() 
												+ "') and contains(text(), '" + account.getAccountName() + "')]/../following-sibling::tr"));
			
			String transRegEx = "(\\d{2} \\w{3}) (.*) ((\\d*,)?\\d+(.)\\d+) (-?(\\d*,)?\\d+(.)\\d+)";
			String transEndRegEx = "Total( (\\d*,)?\\d+(.)\\d+)+ -?(\\d*,)?\\d+(.)\\d+";
			
			String accountNum = account.getAccountNumber();
			accountNum = accountNum.replace("-", "");
			account.setAccountNumber(accountNum);
			
			Pattern pTrans = Pattern.compile(transRegEx);
			Pattern pTransEnd = Pattern.compile(transEndRegEx);
			
			boolean transFound = false;
			
			System.out.println("List size   ::: " + transEle.size());
			double lastBal = Integer.MIN_VALUE;
			BankTransaction lastTrans = null;
			for(WebElement rowEle: transEle){
				
				String rowText = rowEle.getText().trim();
				
				if(rowText.contains("BALANCE B/F")){
					
					if(!transFound){
						transFound = true;
					}
					String tempBal = rowText.substring(rowText.lastIndexOf(" ")).trim();
					tempBal = tempBal.replace(",", "");
					
					lastBal = Double.parseDouble(tempBal);
				}
				
				Matcher m = pTrans.matcher(rowText);
				
				if(m.matches()){
					
					String transDate = m.group(1);
					String desc = m.group(2);
					String amount = m.group(3);
					String runningBalance = m.group(6);
					runningBalance = runningBalance.replaceAll(",", "");
					
					double runBal = Double.parseDouble(runningBalance);
					
					String transType = null;
					if(runBal>lastBal){
						transType = BankTransaction.TRANSACTION_TYPE_CREDIT;
					}
					else{
						transType = BankTransaction.TRANSACTION_TYPE_DEBIT;
					}
					lastBal = runBal;
					BankTransaction bt = new BankTransaction();
					
					bt.setAmount(amount);
					bt.setDescription(desc);
					bt.setTransDate(ParserUtility.getYear(transDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM, 
							account.getBillDate(), ParserUtility.DATEFORMAT_DD_SPACE_MMM_SPACE_YYYY));
					bt.setRunningBalance(runningBalance);
					bt.setTransactionType(transType);
					bt.setCurrency(account.getCurrency());
					bt.setAccountNumber(account.getAccountNumber());
					account.addTransaction(bt);
					
					
				}
				else{
					
						if(transFound){
						
						m = pTransEnd.matcher(rowText);
						
						if(m.matches()){
							System.out.println("Transaction Ends for the account " + account.getAccountNumber());
							break;
						}
						else{
							if(lastTrans != null){
								lastTrans.setDescription(lastTrans.getDescription() + " " + rowText);
							}
						}
						
						
					}
					
				}
				
				
				
			}
			
		}
		
		return response;
	}

}
