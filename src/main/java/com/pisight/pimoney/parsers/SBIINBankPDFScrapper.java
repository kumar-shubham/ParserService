package com.pisight.pimoney.parsers;

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

public class SBIINBankPDFScrapper extends PDFParser {

	@Override
	public Response parse(WebDriver driver, PDDocument pdDocument) throws Exception {
		// TODO Auto-generated method stub
		String page = parsePDFToHTML(pdDocument);

		JavascriptExecutor js = (JavascriptExecutor) driver;

		//System.out.println(page);

		js.executeScript(page);

		return scrapeStatement(driver);
	}

	private Response scrapeStatement(WebDriver driver) {
		// TODO Auto-generated method stub
		Response response = new Response();
		
		//System.out.println("#@#@#@#@##@#@##@#@#@##@#@#@#@#@##@#@#@#@#@#@##@#@#@#@#");
		//System.out.println();
		WebElement page = driver.findElement(By.id("PDF_TO_HTML"));
		//setting default value as INR because of India locale. 
		String currency = "INR";


		WebElement stmtDateEle = page.findElement(By.xpath("//td[contains(text(), 'Date ') and contains(text(),' :')]"));
		String stmtDate = stmtDateEle.getText().trim();
		stmtDate = stmtDate.substring(stmtDate.indexOf(":")+1).trim();

		WebElement accountNumberEle = page.findElement(By.xpath("//td[contains(text(), 'Account Number ') and contains(text(),' :')]"));
		String accountNumber = accountNumberEle.getText().trim();
		accountNumber = accountNumber.substring(accountNumber.indexOf(":")+1).trim();

		WebElement openBalEle = page.findElement(By.xpath("//td[contains(text(), 'Balance as on')]"));
		String openBal = openBalEle.getText().trim();
		openBal = openBal.substring(openBal.indexOf(":")+1).trim();
		openBal = openBal.replace(",", "");

		BankAccount account = new BankAccount();
		account.setAccountNumber(accountNumber);
		account.setBillDate(stmtDate);
		account.setCurrency(currency);
		response.addBankAccount(account);

		String transHeader = "Txn Date Value Description";
		String xpath = "//td[contains(text(), 'Txn Date Value Description')]/../following-sibling::tr";

		//System.out.println("xpath ::: " + xpath);

		List<WebElement> transEle = page.findElements(By.xpath(xpath));

		String transRegEx = "(\\d\\d? \\w{3}( \\d{4})?) (\\d\\d? \\w{3}( \\d{4})?) (.*) ((\\d*,)*\\d+(.)\\d+) ((\\d*,)*\\d+(.)\\d+)";
		String transRegex1 = "(\\d{4}) (\\d{4})?(.*)";

		Pattern pTrans = Pattern.compile(transRegEx);
		Pattern pTrans1 = Pattern.compile(transRegex1);
		Matcher m = null;
		//System.out.println("List size   ::: " + transEle.size());
		double lastBal = Double.parseDouble(openBal);
		BankTransaction lastTrans = null;
		for(WebElement rowEle: transEle){

			String rowText = rowEle.getText().trim();
			//System.out.println("rowtext ::: " + rowText); 
			m = pTrans.matcher(rowText);

			if(m.matches()){
				//System.out.println(11);
				String transDate = m.group(1);
				String desc = m.group(5);
				String amount = m.group(6);
				String transType = null;
				String runningBal = m.group(9);
				runningBal = runningBal.replace(",", "");
				
				double runBal = Double.parseDouble(runningBal);
				

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
				//System.out.println(22);
				
				if(lastTrans != null){
					String temp = lastTrans.getTransDate();
					if(temp.length() < 10){
						//System.out.println("handling date");
						m = pTrans1.matcher(rowText);
						if(m.matches()){
							temp =  temp + " " + m.group(1);
							lastTrans.setTransDate(temp);
						}
					}
				}
				
				if(rowText.contains(transHeader)){
					if(rowText.indexOf(transHeader) >0){
						if(lastTrans != null){
							String temp = rowText.substring(0, rowText.indexOf(transHeader));
							lastTrans.setDescription(lastTrans.getDescription() + " " + temp);
						}
					}
				}
				else if(rowText.contains("This is a computer generated statement")){
					//System.out.println("End of transaction for the account " + accountNumber);
					break;
				}
				else{
					if(lastTrans != null){
						lastTrans.setDescription(lastTrans.getDescription() + " " + rowText);
					}
				}
				

			}


		}
		if(lastTrans != null){
			account.setAccountBalance(lastTrans.getRunningBalance());
		}
		else{
			account.setAccountBalance(openBal);
		}
		
		return response;
	}

}
