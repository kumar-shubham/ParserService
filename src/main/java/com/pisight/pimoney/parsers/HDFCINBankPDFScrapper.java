package com.pisight.pimoney.parsers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import com.pisight.pimoney.beans.Response;
import com.pisight.pimoney.beans.BankAccount;
import com.pisight.pimoney.beans.BankTransaction;

public class HDFCINBankPDFScrapper extends PDFParser {

	@Override
	public Response parse(WebDriver driver, PDDocument pdDocument) throws Exception {
		// TODO Auto-generated method stub
		String page = parsePDFToHTML(pdDocument);

		JavascriptExecutor js = (JavascriptExecutor) driver;

		//System.out.println(page);

		js.executeScript(page);

		return scrapeStatement(driver);
	}

	private Response scrapeStatement(WebDriver driver) throws Exception {
		// TODO Auto-generated method stub
		Response response = new Response();
		
		//System.out.println("#@#@#@#@##@#@##@#@#@##@#@#@#@#@##@#@#@#@#@#@##@#@#@#@#");
		//System.out.println();
		WebElement page = driver.findElement(By.id("PDF_TO_HTML"));
		
		HashSet<String> accountSet = new HashSet<String>();
		List<BankAccount> accounts = new ArrayList<BankAccount>();
		List<WebElement> accountsEle = page.findElements(By.xpath("//td[contains(text(), 'Account No')]"));
		
		WebElement currencyEle = page.findElement(By.xpath("//td[contains(text(), 'Currency :')]"));
		
		String currencyText = currencyEle.getText().trim();
		String currencyRegex = ".*Currency : (\\w{3}).*";
		Pattern p = Pattern.compile(currencyRegex);
		Matcher m = p.matcher(currencyText);
		String currency = null;
		if(m.matches()){
			currency =  m.group(1);
		}
		else{
			throw new Exception("PDF format changed 1. Please verify the pdf and change the parser");
		}
		
		WebElement stmtEle = page.findElement(By.xpath("//td[contains(text(), 'From') and contains(text(), 'To')]"));
		
		String stmtText = stmtEle.getText().trim();
		String stmtRegex = ".*From.*(\\d{2}.\\d{2}.\\d{4}).*To.*(\\d{2}.\\d{2}.\\d{4}).*";
		p = Pattern.compile(stmtRegex);
		m = p.matcher(stmtText);
		String stmtDate = null;
		if(m.matches()){
			stmtDate =  m.group(2);
		}
		else{
			throw new Exception("PDF format changed 2. Please verify the pdf and change the parser");
		}
		
		for (WebElement webElement : accountsEle) {
			
			String accNumText = webElement.getText().trim();
			String accNumRegex = ".*Account No\\.? : (\\d{14}).*";
			p = Pattern.compile(accNumRegex);
			m = p.matcher(accNumText);
			String accountNumber = null;
			if(m.matches()){
				accountNumber =  m.group(1);
			}
			//System.out.println("account ::: " + accountNumber);
			if(!accountSet.contains(accountNumber)){
				BankAccount ba = new BankAccount();
				ba.setAccountNumber(accountNumber);
				ba.setCurrency(currency);
				ba.setBillDate(stmtDate);
				accountSet.add(accountNumber);
				accounts.add(ba);
				response.addBankAccount(ba);
				
				//System.out.println("Account Number ::: " + ba.getAccountNumber());
				//System.out.println("Currency       ::: " + ba.getCurrency());
				//System.out.println("Bill date      ::: " + ba.getBillDate());
			}
			
			
		}
		//System.out.println("account size::: " + accounts.size());
		for(BankAccount account: accounts){
			
			String openBal = null;
			String balance = null;
			if(accounts.size() == 1){
				WebElement balEle = page.findElement(By.xpath("//td[contains(text(), 'Opening Balance') and contains(text(), 'Closing Bal')]/../following-sibling::tr[1]"));
				String balText = balEle.getText().trim();
				String balRegex = "((\\d*,)*\\d+(.)\\d+) .* ((\\d*,)*\\d+(.)\\d+)";
				p = Pattern.compile(balRegex);
				m = p.matcher(balText);
				if(m.matches()){
					openBal = m.group(1);
					balance =  m.group(4);
				}
				else{
					throw new Exception("PDF format changed 3. Please verify the pdf and change the parser");
				}
			}
			else{
				WebElement balEle = page.findElement(By.xpath("//td[contains(text(), 'Account No') and contains(text(), '"
						+ account.getAccountNumber() + "')]/../following-sibling::tr/td[contains(text(), 'Opening Balance')]"));
				
				String balText = balEle.getText().trim();
				String balRegex = "Opening Balance : ((\\d*,)*\\d+(.)\\d+).*";
				p = Pattern.compile(balRegex, Pattern.CASE_INSENSITIVE);
				m = p.matcher(balText);
				if(m.matches()){
					openBal = m.group(1);
				}
				else{
					throw new Exception("PDF format changed 4. Please verify the pdf and change the parser");
				}
				
			}
			openBal = openBal.replace(",", "");
			
			List<WebElement> transEles = page.findElements(By.xpath("//td[contains(text(), 'Account No') and contains(text(), '"
						+ account.getAccountNumber() + "')]/../following-sibling::tr"));
			
			String transRegEx = "";
			if(accounts.size() == 1){
				transRegEx = "(\\d{1,2}.\\d{2}.\\d{2,4}) (.*) ([\\d\\w]{14,16}) (\\d{2}.\\d{2}.\\d{2,4}) (-?(\\d*,)?\\d+(\\.)\\d+) ((\\d*,)?\\d+(\\.)\\d+)";
			}
			else{
				transRegEx = "(\\d{1,2}.\\d{2}.\\d{2,4}) (.*) ((\\d*,)?\\d+(\\.)\\d+) ((\\d*,)?\\d+(\\.)\\d+) ((\\d*,)?\\d+(\\.)\\d+)";
			}

			Pattern pTrans = Pattern.compile(transRegEx);
			//System.out.println("List size   ::: " + transEles.size());
			double lastBal = Double.parseDouble(openBal);
			BankTransaction lastTrans = null;
			boolean transFound = false;
			for(WebElement rowEle:transEles){
				
				String rowText = rowEle.getText().trim();
				
				//System.out.println("rowtext ::: " + rowText); 
				m = pTrans.matcher(rowText);

				if(m.matches()){
					transFound = true;
					//System.out.println(11);
					String transDate = "";
					String desc = "";
					String amount = "";
					String transType = null;
					String runningBal = "";
					if(accounts.size() == 1){
						transDate = m.group(1);
						desc = m.group(2);
						amount = m.group(5);
						runningBal = m.group(8);
					}
					else{
						transDate = m.group(1);
						desc = m.group(2);
						String debit = m.group(3);
						String credit = m.group(6);
						if(debit.equals("0.00")){
							transType = BankTransaction.TRANSACTION_TYPE_CREDIT;
							amount = credit;
						}
						else if(credit.equals("0.00")){
							transType = BankTransaction.TRANSACTION_TYPE_DEBIT;
							amount = debit;
						}
						
						runningBal = m.group(9);
					}
					amount = amount.replace("-", "");
					runningBal = runningBal.replace(",", "");
					
					double runBal = Double.parseDouble(runningBal);
					

					if(transType == null && runBal>lastBal){
						transType = BankTransaction.TRANSACTION_TYPE_CREDIT;
					}
					else if(transType == null){
						transType = BankTransaction.TRANSACTION_TYPE_DEBIT;
					}
					lastBal = runBal;
					

					if(transDate.length() < 10 && transDate.contains("/") ){
						transDate = transDate.substring(0, transDate.lastIndexOf("/")+1) + "20" + transDate.substring(transDate.lastIndexOf("/")+1); 
					}
					BankTransaction bt = new BankTransaction();

					bt.setAmount(amount);
					bt.setDescription(desc);
					bt.setTransDate(transDate);
					bt.setTransactionType(transType);
					bt.setAccountNumber(account.getAccountNumber());
					bt.setCurrency(currency);
					bt.setRunningBalance(runningBal);
					account.addTransaction(bt);
					lastTrans = bt;
					//System.out.println();
					//System.out.println();
					//System.out.println("Transaction Desc    ::: " + bt.getDescription());
					//System.out.println("Transaction amount  ::: " + bt.getAmount());
					//System.out.println("Transaction date    ::: " + bt.getTransDate());
					//System.out.println("Transaction type    ::: " + bt.getTransactionType());
					//System.out.println("Account Number      ::: " + bt.getAccountNumber());
					//System.out.println();
					//System.out.println();

				}
				else{
					if(transFound){
						//System.out.println("in else");
						if(rowText.toLowerCase().contains("opening balance") && rowText.toLowerCase().contains("closing bal")){
							//System.out.println("End of transaction for the account " + account.getAccountNumber());
							break;
						}
						else if(rowText.toUpperCase().contains("HDFC BANK LIMITED") || rowText.toLowerCase().contains("fd may be linked to other accounts")){
							//System.out.println("Transaction end for the page. Moving to next page.");
							transFound = false;
						}
						else{
							lastTrans.setDescription(lastTrans.getDescription()+rowText);
						}
					}
				}
			}
			if(lastTrans != null){
				account.setAccountBalance(lastTrans.getRunningBalance());
			}
			else{
				if(balance != null){
					account.setAccountBalance(openBal);
				}
				else{
					account.setAccountBalance(openBal);
				}
			}
			
		}
		
		return response;
	}

}
