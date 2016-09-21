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
import com.pisight.pimoney.beans.BankAccount;
import com.pisight.pimoney.beans.BankTransaction;

public class ICICISGBankPDFScrapper extends PDFParser {

	@Override
	public Response parse(WebDriver driver, File file) throws Exception {
		// TODO Auto-generated method stub
		String page = parsePDFToHTML(file);

		JavascriptExecutor js = (JavascriptExecutor) driver;

		System.out.println(page);

		js.executeScript(page);

		return scrapeStatement(driver);
	}

	private Response scrapeStatement(WebDriver driver) {
		// TODO Auto-generated method stub
		Response response  = new Response();
		
		System.out.println("#@#@#@#@##@#@##@#@#@##@#@#@#@#@##@#@#@#@#@#@##@#@#@#@#");
		System.out.println();
		WebElement page = driver.findElement(By.id("PDF_TO_HTML"));
		
		
		WebElement currencyEle = page.findElement(By.xpath("//td[contains(text(), 'ACCOUNT DETAILS -')]"));
		
		String currency = currencyEle.getText().trim();
		currency = currency.replace("ACCOUNT DETAILS -", "").trim();
		
		WebElement stmtDateEle = page.findElement(By.xpath("//td[contains(text(), 'Summary of Accounts held under Cust ID')]"));
		
		String stmtDate = stmtDateEle.getText().trim();
		stmtDate = stmtDate.substring(stmtDate.indexOf("as on ")+5).trim();
		
		List<BankAccount> accounts  = new ArrayList<BankAccount>();
		
		List<WebElement> accountEle = page.findElements(By.xpath("//td[contains(text(), 'Summary of Accounts held under Cust ID')]/../following-sibling::tr"));
		
		String accountRegEx = "(.*) (\\d{12}) ((\\d*,)*\\d+(.)\\d+) .*";
		String accountEndRegEx = "TOTAL( (\\d*,)*\\d+(.)\\d+){3}.*";
		
		Pattern pAccount = Pattern.compile(accountRegEx);
		Pattern pAccountEnd = Pattern.compile(accountEndRegEx);
		
		boolean accountsFound = false;
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
				
				String accountName = m.group(1);
				String accountNumber = m.group(2);
				String balance = m.group(3);
				
				BankAccount ba = new BankAccount();
				
				ba.setAccountBalance(balance);
				ba.setAccountNumber(accountNumber);
				ba.setAccountName(accountName);
				ba.setCurrency(currency);
				ba.setBillDate(stmtDate);
				accounts.add(ba);
				response.addBankAccount(ba);
				System.out.println();
				System.out.println("Account Balance      ::: " + balance);
				System.out.println("Account Currency     ::: " + currency);
				System.out.println("Account Number       ::: " + accountNumber);
				System.out.println("Account Name         ::: " + accountName);
				System.out.println("Statement Date       ::: " + stmtDate);
				System.out.println();
			}
			else{
				System.out.println("4");
				
				if(accountsFound){
					
						m = pAccountEnd.matcher(rowText);
						
						if(m.matches()){
							System.out.println("All accounts scrapped. Now Skipping the loop.");
							break;
						}
				}
			}
			
		}
		
		for(BankAccount account:accounts){
			
			String identifier = "Statement of Transactions in Savings Account Number:" + " " + account.getAccountNumber();
			
			System.out.println("identifier  ::: " + identifier);
			
			List<WebElement> transEle = page.findElements(By.xpath("//td[contains(text(), '" + identifier + "')]/../following-sibling::tr"));
			
			String transRegEx = "(\\d{2}-\\d{2}-\\d{4}) (.*) ((\\d*,)*\\d+(.)\\d+) ((\\d*,)*\\d+(.)\\d+)";
			String transEndRegEx = "TOTAL (.*) ((\\d*,)*\\d+(.)\\d+){3}.*";
			
			Pattern pTrans = Pattern.compile(transRegEx);
			Pattern pTransEnd = Pattern.compile(transEndRegEx);
			
			boolean transFound = false;
			
			System.out.println("List size   ::: " + transEle.size());
			double lastBal = Integer.MIN_VALUE;
			BankTransaction lastTrans = null;
			for(WebElement rowEle: transEle){
				
				String rowText = rowEle.getText().trim();
				System.out.println("rowtext ::: " + rowText);
				if(rowText.contains("Balance Brought Forward")){
					
					if(!transFound){
						System.out.println("Transaction starts for the account " + account.getAccountNumber());
						transFound = true;
					}
					String tempBal = rowText.substring(rowText.lastIndexOf(" ")).trim();
					tempBal = tempBal.replace(",", "");
					
					lastBal = Double.parseDouble(tempBal);
				}
				
				Matcher m = pTrans.matcher(rowText);
				
				if(m.matches()){
					System.out.println(11);
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
					bt.setTransDate(transDate);
					bt.setRunningBalance(runningBalance);
					bt.setTransactionType(transType);
					bt.setCurrency(currency);
					bt.setAccountNumber(account.getAccountNumber());
					account.addTransaction(bt);
					lastTrans = bt;
					System.out.println();
    				System.out.println();
    				System.out.println("Transaction Desc    ::: " + lastTrans.getDescription());
    				System.out.println("Transaction amount  ::: " + lastTrans.getAmount());
    				System.out.println("Transaction date    ::: " + lastTrans.getTransDate());
    				System.out.println("Transaction type    ::: " + lastTrans.getTransactionType());
    				System.out.println("Transaction balance ::: " + lastTrans.getRunningBalance());
    				System.out.println("Transaction currency::: " + lastTrans.getCurrency());
    				System.out.println();
    				System.out.println();
					
					
				}
				else{
					System.out.println(22);
						if(transFound){
							System.out.println(33);
						m = pTransEnd.matcher(rowText);
						
						if(m.matches()){
							System.out.println("Transaction Ends for the account " + account.getAccountNumber());
							break;
						}
						else{
							System.out.println(44);
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
