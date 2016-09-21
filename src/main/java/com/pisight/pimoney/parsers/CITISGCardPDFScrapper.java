package com.pisight.pimoney.parsers;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import com.pisight.pimoney.beans.Response;
import com.pisight.pimoney.beans.CardAccount;
import com.pisight.pimoney.beans.CardTransaction;
import com.pisight.pimoney.beans.ParserUtility;

public class CITISGCardPDFScrapper extends PDFParser {

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
		
		HashMap<String, CardAccount> map = new HashMap<String, CardAccount>();
		
		try{
			WebElement stmtDateEle = page.findElement(By.xpath("//td[contains(text(), 'Statement Date')]"));
			
			String stmtDate = stmtDateEle.getText().trim();
			stmtDate = stmtDate.substring(stmtDate.indexOf("Statement Date")+15).trim();
			
			WebElement creditLimitEle = page.findElement(By.xpath("//td[contains(text(), 'Credit Limit ')]"));
			
			String creditLimit = creditLimitEle.getText().trim();
			creditLimit = creditLimit.substring(creditLimit.indexOf("Credit Limit ")+12).trim();
			creditLimit = creditLimit.replace("$", "").trim();
			
			WebElement dueDateEle = page.findElement(By.xpath("//td[contains(text(), 'Payment Due Date')]"));
			
			String dueDate = dueDateEle.getText().trim();
			dueDate = dueDate.substring(dueDate.indexOf("Payment Due Date")+17).trim();
			
			
			
			List<WebElement> accountEle = page.findElements(By.xpath("//td[contains(text(), 'CREDIT CARD TYPE ACCOUNT NUMBER CURRENT BALANCE')]/../following-sibling::tr"));
			
			String accountRegEx = "\\d (.*) ((\\d ?){16}) ((\\d*,)?\\d+(.)\\d+) ((\\d*,)?\\d+(.)\\d+).*";
			
			Pattern pAccount = Pattern.compile(accountRegEx);
			
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
					String amountDue = m.group(4);
					String minPayment = m.group(7);
					
					accountNumber = accountNumber.replace(" ", "");
					
					CardAccount ca = new CardAccount();
					
					ca.setAccountNumber(accountNumber);
					ca.setAccountName(accountName);
					ca.setBillDate(stmtDate);
					ca.setAmountDue(amountDue);
					ca.setMinAmountDue(minPayment);
					ca.setDueDate(dueDate);
					ca.setTotalLimit(creditLimit);
					response.addCardAccount(ca);
					map.put(accountNumber, ca);
					
					System.out.println();
					System.out.println("Account Number       ::: " + accountNumber);
					System.out.println("Account Name         ::: " + accountName);
					System.out.println("Amount Due      ::: " + amountDue);
					System.out.println("Statement Date       ::: " + stmtDate);
					System.out.println("Minimum Payment      ::: " + minPayment);
					System.out.println("Credit Limit         ::: " + creditLimit);
					System.out.println("Due Date             ::: " + dueDate);
					System.out.println();
				}
				else{
					System.out.println("4");
					
					if(accountsFound){
						
							if(rowText.contains("TOTAL FOR THE CARD(S)")){
								System.out.println("All accounts scrapped. Now Skipping the loop.");
								break;
							}
					}
				}
				
			}
		}
		catch(NoSuchElementException e){
			System.out.println("Error in card account scrapping.  Moving to search for transactions");
			e.printStackTrace();
		}
		
		String headerRegex = "(.*)(( \\d{4}){4}) Payment Due Date:(.*)";
		Pattern p = Pattern.compile(headerRegex);
		
		List<WebElement> transListEle = page.findElements(By.xpath("//td[contains(text(), ' Payment Due Date: ')]"));
		
		//to skip the duplicate transactions in case of multipage transactions
		String lastText = "";
		for(WebElement ele:transListEle){
			
			String text = ele.getText().trim();
			if(lastText.equals(text)){
				System.out.println("Transactions already scrapped. So skipping");
				continue;
			}
			lastText = text;
			Matcher m = p.matcher(text);
			
			//accountEnd regex is present two times in a transaction table so keeping a count and if it is 2 then breaking the transaction loop
			int markerCount = 0;
			if(m.matches()){
				
				String accountName = m.group(1);
				String accountNumber = m.group(2);
				String accDueDate = m.group(4).trim();
				
				String xpath = "//td[contains(text(), '" + accountNumber + "') and contains(text(), '" +
						accountName + "') and contains(text(), 'Payment Due Date:')]/../following-sibling::tr";
				
				accountNumber = accountNumber.replace(" ", "").trim();
				CardAccount account = null;
				if(map.get(accountNumber) == null){
					account = new CardAccount();
					account.setAccountNumber(accountNumber);
					map.put(accountNumber, account);
					response.addCardAccount(account);
				}
				else{
					account = map.get(accountNumber);
				}
			
			
			System.out.println("xpath ::: " + xpath);
			 
			List<WebElement> transEle = page.findElements(By.xpath(xpath));
			
			String transRegEx = "(\\d{2} \\w{3}) (.*) \\d{3} (\\(?((\\d*,)?\\d+(.)\\d+\\)?))";
			String transEndRegEx = "SUB-TOTAL: (\\(?((\\d*,)?\\d+(.)\\d+\\)?))";
			
			Pattern pTrans = Pattern.compile(transRegEx);
			Pattern pTransEnd = Pattern.compile(transEndRegEx);
			
			boolean transFound = false;
			System.out.println("List size   ::: " + transEle.size());
			for(WebElement rowEle: transEle){
				
				String rowText = rowEle.getText().trim();
				System.out.println("rowtext ::: " + rowText);
				m = pTrans.matcher(rowText);
				
				if(m.matches()){
					if(!transFound){
						System.out.println("Transaction starts for the account " + account.getAccountNumber());
						transFound = true;
					}
					System.out.println(11);
					String transDate = m.group(1);
					String desc = m.group(2);
					String amount = m.group(3);
					String transType = null;
					
					String refDate = account.getBillDate();
					if(refDate.equals("")){
						refDate = accDueDate;
					}
					transDate = ParserUtility.getYear(transDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM, 
							refDate, ParserUtility.DATEFORMAT_MMMM_SPACE_DD_COMMA_SPACE_YYYY);
					
					if(amount.contains("(")){
						transType = CardTransaction.TRANSACTION_TYPE_CREDIT;
					}
					else{
						transType = CardTransaction.TRANSACTION_TYPE_DEBIT;
					}
					
					amount = amount.replace("(", "");
					amount = amount.replace(")", "");
					
					CardTransaction bt = new CardTransaction();
					
					bt.setAmount(amount);
					bt.setDescription(desc);
					bt.setTransDate(transDate);
					bt.setTransactionType(transType);
					bt.setAccountNumber(account.getAccountNumber());
					account.addTransaction(bt);
					System.out.println();
    				System.out.println();
    				System.out.println("Transaction Desc    ::: " + bt.getDescription());
    				System.out.println("Transaction amount  ::: " + bt.getAmount());
    				System.out.println("Transaction date    ::: " + bt.getTransDate());
    				System.out.println("Transaction type    ::: " + bt.getTransactionType());
    				System.out.println("Account Number      ::: " + bt.getAccountNumber());
    				System.out.println();
    				System.out.println();
					
				}
				else{
					System.out.println(22);
						if(transFound){
							System.out.println(33);
						m = pTransEnd.matcher(rowText);
						
						if(m.matches()){
							markerCount++;
							if(markerCount > 1){
								System.out.println("Transaction Ends for the account " + account.getAccountNumber());
								break;
							}
						}
						
						
					}
					
				}
				
			}
				
			}
			
		}
		
		return response;
	}

}
