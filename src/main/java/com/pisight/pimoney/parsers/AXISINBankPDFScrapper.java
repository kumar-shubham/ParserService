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

public class AXISINBankPDFScrapper extends PDFParser {

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


		String xpath = "//td[contains(text(), 'Account No.') and contains(text(), 'Quick View')]";

		List<WebElement> accountEle = page.findElements(By.xpath(xpath));

		boolean transFound = false;
		double lastBal = Integer.MIN_VALUE;
		
		for(WebElement rowEle: accountEle){
			BankAccount ba = new BankAccount();
			//System.out.println("1");
			String rowText = rowEle.getText().trim();

			String accNumRegEx = ".*Account No.*(\\d{15}).*Quick View.*";

			Pattern p = Pattern.compile(accNumRegEx);

			Matcher m = p.matcher(rowText);
			String accountNumber = null;
			if(m.matches()){
				accountNumber = m.group(1);
				response.addBankAccount(ba);
			}
			else{
				//System.out.println("Something is wrong.");
				continue;
			}
			xpath = "//td[contains(text(), 'Account No.') and contains(text(), 'Quick View') and contains(text(), '" + 
					accountNumber + "')]/../following-sibling::tr";

			List<WebElement> eles = page.findElements(By.xpath(xpath));

			String stmtDate = null;
			String currency = null;
			String balance = null;
			BankTransaction lastTrans = null;
			for(WebElement ele:eles){

				String text = ele.getText().trim();

				if(stmtDate == null){
					String stmtDateRegEx = ".*\\d{15}.*\\d{2}-\\d{2}-\\d{4}.*(\\d{2}-\\d{2}-\\d{4}).*";
					p = Pattern.compile(stmtDateRegEx);
					m = p.matcher(text);
					if(m.matches()){
						stmtDate = m.group(1);
					}
				}
				if(currency == null){
					String currencyRegEx = ".* Currency : (\\w{3}).*";
					p = Pattern.compile(currencyRegEx);
					m = p.matcher(text);
					if(m.matches()){
						currency = m.group(1);
					}
				}

				if(text.contains("Opening Balance")){

					if(!transFound){
						//System.out.println("Transaction starts for the account " + accountNumber);
						transFound = true;
					}
					String tempBal = text.substring(text.lastIndexOf(" ")).trim();
					tempBal = tempBal.replace(",", "");

					lastBal = Double.parseDouble(tempBal);
				}

				if(transFound){

					String transRegEx = "(\\d{2}-\\d{2}-\\d{4}) (.*) ((\\d*,)?\\d+(.)\\d+) ((\\d*,)?\\d+(.)\\d+)";

					Pattern pTrans = Pattern.compile(transRegEx);

					m = pTrans.matcher(text);

					if(m.matches()){
						//System.out.println(11);
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
						bt.setAccountNumber(accountNumber);
						ba.addTransaction(bt);
						lastTrans = bt;
						//System.out.println();
						//System.out.println();
						//System.out.println("Transaction Desc    ::: " + lastTrans.getDescription());
						//System.out.println("Transaction amount  ::: " + lastTrans.getAmount());
						//System.out.println("Transaction date    ::: " + lastTrans.getTransDate());
						//System.out.println("Transaction type    ::: " + lastTrans.getTransactionType());
						//System.out.println("Transaction balance ::: " + lastTrans.getRunningBalance());
						//System.out.println("Transaction currency::: " + lastTrans.getCurrency());
						//System.out.println();
						//System.out.println();


					}else{
						if(transFound){
							//System.out.println(33);

							if(text.contains("Closing Balance")){
								//System.out.println("Transaction Ends for the account " + accountNumber);
								balance = text.substring(text.lastIndexOf(" ")).trim();
								break;
							}
							else{
								//System.out.println(44);
								if(lastTrans != null){
									lastTrans.setDescription(lastTrans.getDescription() + " " + text);
								}
							}


						}
					}

				}



			}


			ba.setAccountNumber(accountNumber);
			ba.setCurrency(currency);
			ba.setBillDate(stmtDate);
			ba.setAccountBalance(balance);
			//System.out.println();
			//System.out.println("Account Currency     ::: " + currency);
			//System.out.println("Account Number       ::: " + accountNumber);
			//System.out.println("Statement Date       ::: " + stmtDate);
			//System.out.println();

		}

		
		return response;
	}

}
