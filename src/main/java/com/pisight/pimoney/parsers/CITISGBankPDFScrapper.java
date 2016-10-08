package com.pisight.pimoney.parsers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import com.pisight.pimoney.beans.BankAccount;
import com.pisight.pimoney.beans.BankTransaction;
import com.pisight.pimoney.beans.Response;

public class CITISGBankPDFScrapper extends PDFParser {

	@Override
	public Response parse(WebDriver driver, PDDocument pdDocument) throws Exception {
		// TODO Auto-generated method stub
		String page = parsePDFToHTML(pdDocument);

		JavascriptExecutor js = (JavascriptExecutor) driver;

		//System.out.println(page);

		js.executeScript(page);

		return scrapeStatement(driver);
	}

	private Response scrapeStatement(WebDriver driver){
		
		Response response = new Response();
		
		//System.out.println("#@#@#@#@##@#@##@#@#@##@#@#@#@#@##@#@#@#@#@#@##@#@#@#@#");
		//System.out.println();
		WebElement page = driver.findElement(By.id("PDF_TO_HTML"));
		
		WebElement detailEle = page.findElement(By.xpath("//td[contains(text(), 'Page 2 of')]/../following-sibling::tr[1]"));
		
		String detailText = detailEle.getText().trim();
		
		String accountHolder = detailText.substring(0, detailText.indexOf("Statement Period")).trim();
		
		String statementDate = detailText.substring(detailText.length()-11);
		
		//System.out.println("Account Holder       ::: " + accountHolder);
		//System.out.println("Statement Date       ::: " + statementDate);
		
		List<WebElement> accountEle  = page.findElements(By.xpath("//td[contains(text(), 'SUMMARY OF YOUR CITI PRIORITY ACCOUNT')]/../following-sibling::tr"));
		
		String accountInProgress = null;
		int typeCount = 0;
		
		String pattern = ".*\\d+(.)\\d+$";

	    Pattern r = Pattern.compile(pattern);

//	    Matcher m = r.matcher(line);
	    
	    List<BankAccount> accounts = new ArrayList<BankAccount>();
		for(WebElement rowWEle: accountEle){
			
			String rowText = rowWEle.getText().trim();
			
			if(rowText.equalsIgnoreCase("Savings & Investments")){
				accountInProgress = "Savings & Investments";
				typeCount++;
				continue;
			}
			else if(rowText.equalsIgnoreCase("Checking")){
				accountInProgress = "Checking";
				typeCount++;
				continue;
			}
			
			if(accountInProgress != null && rowText.contains(accountInProgress + " Total")){
				//System.out.println();
				//System.out.println(" end of " + accountInProgress + " accounts");
				//System.out.println();
				accountInProgress = null;
				continue;
			}
			
			if(typeCount == 2 && accountInProgress == null){
				//System.out.println("Account scrapping done. exiting the loop.");
				break;
			}
			
			
			
			if(accountInProgress != null){
				String balance = rowText.substring(rowText.lastIndexOf(" ")).trim();
				rowText = rowText.replace(balance, "").trim();
				
				Matcher m = r.matcher(balance);
				
				if(!m.matches()){
					//System.out.println("not the required line. So skipping.");
					continue;
				}
				
				String currency = rowText.substring(rowText.lastIndexOf(" ")).trim();
				rowText = rowText.replace(currency, "").trim();
				
				String accountNumber = rowText.substring(rowText.lastIndexOf(" ")).trim();
				rowText = rowText.replace(accountNumber, "").trim();
				
				String accountName = rowText;	
				
				if(accountName.equalsIgnoreCase("Brokerage")){
					//System.out.println("investment account. So skipping.");
					continue;
				}
				
				//System.out.println();
				//System.out.println("Account Balance      ::: " + balance);
				//System.out.println("Account Currency     ::: " + currency);
				//System.out.println("Account Number       ::: " + accountNumber);
				//System.out.println("Account Name       ::: " + accountName);
				//System.out.println();
				
			
				BankAccount ba = new BankAccount();
				ba.setAccountBalance(balance);
				ba.setAccountHolder(accountHolder);
				ba.setAccountNumber(accountNumber);
				ba.setCurrency(currency);
				ba.setBillDate(statementDate);
				ba.setAccountName(accountName);
				
				accounts.add(ba);
				response.addBankAccount(ba);
				
				
				if(accountInProgress.equalsIgnoreCase("Checking")){
					
				}
			}
			
		}
		
		
		List<WebElement> transactionEle  = null;
		
		accountInProgress = null;
		typeCount = 0;
		
		String patternEnd = "TOTAL (\\d*,)?\\d+(.)\\d+ (\\d*,)?\\d+(.)\\d+$";
		String patternTrans = "\\w{3} \\d{2} \\d{4}(.*)(\\d*,)?\\d+(.)\\d+ (\\d*,)?\\d+(.)\\d+$";

	    Pattern rEnd = Pattern.compile(patternEnd);
	    Pattern rTrans = Pattern.compile(patternTrans);
	    
	    Matcher m = null;
	    
	    for(BankAccount account: accounts){
	    	
	    	double lastBal = Integer.MIN_VALUE;
	    	boolean isTransStarted = false;
	    	String acc = account.getAccountName() + " " + account.getAccountNumber() +  " " + account.getCurrency();
	    	transactionEle = page.findElements(By.xpath("//td[text() = '" + acc + "']/../following-sibling::tr"));
	    	//System.out.println();
	    	//System.out.println();
	    	String transDate = null;
	    	String amount = null;
	    	String desc = null;
	    	String runningBalance = null;
	    	String transType = null;
	    	BankTransaction lastTrans = null;
	    	for(WebElement rowEle: transactionEle){
	    		
	    		String rowText = rowEle.getText().trim();
//	    		//System.out.println(rowText);
	    		
	    		m = rTrans.matcher(rowText);
	    		
	    		if(lastBal == Integer.MIN_VALUE && rowText.contains("OPENING BALANCE")){
	    			
	    			String temp = rowText.substring(rowText.lastIndexOf(" ")).trim();
	    			temp = temp.replace(",", "").trim();
	    			
	    			lastBal = Double.parseDouble(temp);
	    			//System.out.println();
	    			//System.out.println(" start of transactions for the account ::: " + account);
	    			//System.out.println();
	    			continue;
	    			
	    		}
	    		
	    		if(m.matches()){
	    			
//	    			//System.out.println("1");
	    			if(isTransStarted){
	    				
	    				//System.out.println();
	    				//System.out.println();
	    				//System.out.println("Transaction Desc    ::: " + lastTrans.getDescription());
	    				//System.out.println("Transaction amount  ::: " + lastTrans.getAmount());
	    				//System.out.println("Transaction date    ::: " + lastTrans.getTransDate());
	    				//System.out.println("Transaction type    ::: " + lastTrans.getTransactionType());
	    				//System.out.println("Transaction balance ::: " + lastTrans.getRunningBalance());
	    				//System.out.println();
	    				//System.out.println();
	    			}
	    			
	    			runningBalance = rowText.substring(rowText.lastIndexOf(" ")).trim();
	    			rowText = rowText.replace(runningBalance, "").trim();
	    			
	    			amount = rowText.substring(rowText.lastIndexOf(" ")).trim();
	    			rowText = rowText.replace(amount, "").trim();
	    			
	    			transDate = rowText.substring(0, 11);
	    			
	    			desc = rowText.substring(23);
	    			
	    			runningBalance = runningBalance.replace(",", "");
	    			
	    			double temp = Double.parseDouble(runningBalance);
	    			if( temp > lastBal){
	    				
	    				transType = BankTransaction.TRANSACTION_TYPE_CREDIT;
	    				
	    			}
	    			else{
	    				transType = BankTransaction.TRANSACTION_TYPE_DEBIT;
	    			}
	    			
	    			lastBal = temp;
	    			
	    			BankTransaction bt = new BankTransaction();
	    			bt.setAmount(amount);;
	    			bt.setDescription(desc);
	    			bt.setTransDate(transDate);
	    			bt.setRunningBalance(runningBalance);
	    			bt.setTransactionType(transType);
	    			bt.setAccountNumber(account.getAccountNumber());
	    			if(account.getCurrency() != null && !account.getCurrency().equals(" ")){
	    				bt.setCurrency(account.getCurrency());
	    			}
	    			account.addTransaction(bt);
	    			lastTrans = bt;
	    			
	    			if(!isTransStarted){
	    				isTransStarted = true;
	    			}
	    			
	    		}
	    		else{
	    			
	    			m = rEnd.matcher(rowText);
		    		
		    		if(m.matches()){
		    			//System.out.println();
		    			//System.out.println();
		    			//System.out.println(" end of transactions for the account ::: " + account);
		    			//System.out.println();
		    			isTransStarted = false;
		    			break;
		    		}
		    		
		    		if((rowText.contains("Page ") && rowText.contains(" of ")) || rowText.contains(accountHolder)
		    				|| rowText.contains("Transactions Done") || rowText.contains("(continued)") 
		    				|| rowText.contains("CLOSING BALANCE")){
		    			//System.out.println();
		    			//System.out.println("not a transaction. So skipping.");
		    			continue;
		    		}
		    		if(isTransStarted){
		    			lastTrans.setDescription(lastTrans.getDescription() + " " + rowText.trim());
		    		}
	    			
	    		}
	    		
	    		
	    	}
	    	
	    	if(lastTrans != null){
		    	//System.out.println("Last transaction -->> ");
				//System.out.println();
				//System.out.println("Transaction Desc    ::: " + lastTrans.getDescription());
				//System.out.println("Transaction amount  ::: " + lastTrans.getAmount());
				//System.out.println("Transaction date    ::: " + lastTrans.getTransDate());
				//System.out.println("Transaction type    ::: " + lastTrans.getTransactionType());
				//System.out.println("Transaction balance ::: " + lastTrans.getRunningBalance());
				//System.out.println();
				//System.out.println();
	    	}
	    	
	    	//System.out.println();
	    	//System.out.println();
	    
	    	
	    }
		return response;
	}

	
}
