package com.pisight.pimoney.parsers;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
import com.pisight.pimoney.beans.ParserUtility;
import com.pisight.pimoney.beans.Response;

public class CIMBSGBankPDFScrapper extends PDFParser {

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

		List<BankAccount> accounts = new ArrayList<BankAccount>();

		List<WebElement> accountEle = page.findElements(By.xpath("//td[contains(text(), 'ACCOUNT TYPE ACCOUNT NO. FOR PERIOD CURRENCY')]/../following-sibling::tr[1]"));

		for(WebElement ele: accountEle){

			String text = ele.getText().trim();
			String accountRegex = "(.*) (\\d{10}) (\\w{3} \\d{4}) (\\w{3})";
			Pattern p = Pattern.compile(accountRegex);
			Matcher m = p.matcher(text);

			if(m.matches()){
				String accountName = m.group(1);
				String accountNumber = m.group(2);
				String stmtDate = m.group(3);
				String currency = m.group(4);
				
				if(stmtDate.length() < 9){
					
					SimpleDateFormat sdf = new SimpleDateFormat("MMM yyyy");
					Date date = sdf.parse(stmtDate);
					Calendar c = Calendar.getInstance();
					c.setTime(date);
					int day = c.getActualMaximum(Calendar.DAY_OF_MONTH);
					//System.out.println("max dayyyy ::: " + day);
					stmtDate = day + " " + stmtDate;
				}

				BankAccount ba = new BankAccount();
				ba.setAccountName(accountName);
				ba.setAccountNumber(accountNumber);
				ba.setBillDate(stmtDate);
				ba.setCurrency(currency);
				accounts.add(ba);
				response.addBankAccount(ba);

				//System.out.println("Account Name             ::: " + ba.getAccountName());
				//System.out.println("Account Number           ::: " + ba.getAccountNumber());
				//System.out.println("Bill date                ::: " + ba.getBillDate());
				//System.out.println("Currency                 ::: " + ba.getCurrency());

				String xpath = "//td[contains(text(), '" + accountName + "') and contains(text(), '" + accountNumber + "')]/../following-sibling::tr";

				List<WebElement> transEle = page.findElements(By.xpath(xpath));
				boolean transFound = false;

				//System.out.println("List size   ::: " + transEle.size());
				double lastBal = Integer.MIN_VALUE;
				BankTransaction lastTrans = null;
				boolean newDesc = false;
				for(WebElement rowEle: transEle){

					String rowText = rowEle.getText().trim();

					//System.out.println("rowtext ::: " + rowText);
					if(rowText.toLowerCase().contains("balance brought forward")){

						if(!transFound){
							//System.out.println("Transaction starts for the account " + ba.getAccountNumber());
							transFound = true;
						}
						String tempBal = rowText.substring(rowText.lastIndexOf(" ")).trim();
						tempBal = tempBal.replace(",", "");

						lastBal = Double.parseDouble(tempBal);
					}

					String transRegex = "(\\d{2} \\w{3}) (.*) (((\\d ?)*, ?)*(\\d ?)+( ?\\. ?)(\\d ?){2}) ?(((\\d ?)*, ?)*(\\d ?)+( ?\\. ?)(\\d ?){2})";
					p = Pattern.compile(transRegex);
					m = p.matcher(rowText);

					if(m.matches()){
						newDesc = true;
						//System.out.println(11);
						String transDate = m.group(1);
						String desc = m.group(2);
						String amount = m.group(3);
						String runningBalance = m.group(9);
						amount = amount.replace(" ", "");
						runningBalance = runningBalance.replace(" ", "");
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
						
						transDate = ParserUtility.getYear(transDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM, 
										stmtDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM_SPACE_YYYY);

						BankTransaction bt = new BankTransaction();

						bt.setAmount(amount);
						bt.setDescription(desc);
						bt.setTransDate(transDate);
						bt.setRunningBalance(runningBalance);
						bt.setTransactionType(transType);
						bt.setCurrency(currency);
						bt.setAccountNumber(ba.getAccountNumber());
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


					}
					else{
						//System.out.println(22);
						if(transFound){
							//System.out.println(33);

							if(rowText.toLowerCase().contains("balance carried forward")){
								//System.out.println("Transaction Ends for the account " + ba.getAccountNumber());
								String balance = rowText.substring(rowText.lastIndexOf(" "));
								ba.setAccountBalance(balance);
								break;
							}
							else{
								//System.out.println(44);
								if(lastTrans != null && newDesc){
									newDesc = false;
									lastTrans.setDescription(lastTrans.getDescription() + " " + rowText);
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
