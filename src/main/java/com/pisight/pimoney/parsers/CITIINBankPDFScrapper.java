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

public class CITIINBankPDFScrapper extends PDFParser {

	@Override
	public Response parse(WebDriver driver, PDDocument pdDocument) throws Exception {
		// TODO Auto-generated method stub
		//calling overloaded method of pdfextrator for parsing transactions line correctly
		//arguments -> regex to match(in this case it is amount), starttext(usually withdrawal/debit in table header...
		//...end text(end of withdrawal column, can be null), markertext(text to be added for identification...
		//...halttext(the function should stop adding markertext after it meets halttext
		
		String page = parsePDFToHTML(pdDocument, "((\\d*,)*\\d+(\\.)\\d+)", "Withdrawals (", ")", "(DR)", "CLOSING BALANCE");

		JavascriptExecutor js = (JavascriptExecutor) driver;

		//System.out.println(page);

		js.executeScript(page);

		return scrapeStatement(driver);
	}

	private Response scrapeStatement(WebDriver driver) {
		// TODO Auto-generated method stub
		Response response =  new Response();
		
		//System.out.println("#@#@#@#@##@#@##@#@#@##@#@#@#@#@##@#@#@#@#@#@##@#@#@#@#");
		//System.out.println();
		WebElement page = driver.findElement(By.id("PDF_TO_HTML"));

		List<BankAccount> accounts = new ArrayList<BankAccount>();

		WebElement stmtEle = page.findElement(By.xpath("//td[contains(text(), 'Account Statement as on')]"));
		String stmtText = stmtEle.getText().trim();
		String stmtDate = stmtText.substring(stmtText.indexOf("as on")+5).trim();


		String xpathExpression = "//td[contains(text(), 'Details for Account Number:')]";
		List<WebElement> accountsEle = page.findElements(By.xpath(xpathExpression));

		for(WebElement accEle: accountsEle){

			String text = accEle.getText().trim();

			//System.out.println("text ::: " + text);

			String accountRegex = ".*Details for Account Number: ([\\d\\w-]{12}) In (\\w{3}).*";
			Pattern p = Pattern.compile(accountRegex, Pattern.CASE_INSENSITIVE);
			Matcher m = p.matcher(text);
			if(m.matches()){
				String accountNumber = m.group(1);
				String currency = m.group(2);
				BankAccount ba = new BankAccount();
				ba.setAccountNumber(accountNumber);
				ba.setCurrency(currency);
				ba.setBillDate(stmtDate);
				accounts.add(ba);
				response.addBankAccount(ba);
			}
		}

		for(BankAccount account: accounts){

			String xpath = "//td[contains(text(), 'Details for Account Number:') and contains(text(), '" + 
					account.getAccountNumber() + "')]/../following-sibling::tr";

			List<WebElement> transEle = page.findElements(By.xpath(xpath));

			String transRegex1 = "(\\d{2}.?\\w{3}.?\\d{2}) (.*) (\\(DR\\))?((\\d*,)*\\d+(\\.)\\d+) ((\\d*,)*\\d+(\\.)\\d+)";
			String transRegex2 = "(\\d{2}.?\\w{3}.?\\d{2}) (.*) (\\(DR\\))?((\\d*,)*\\d+(\\.)\\d+)";
			Pattern p1 = Pattern.compile(transRegex1, Pattern.CASE_INSENSITIVE);
			Pattern p2 = Pattern.compile(transRegex2, Pattern.CASE_INSENSITIVE);

			//System.out.println("List size   ::: " + transEle.size());
			double lastBal = Integer.MIN_VALUE;
			BankTransaction lastTrans = null;
			boolean transFound = false;
			for(WebElement ele : transEle){

				String text = ele.getText().trim();
//				//System.out.println("text -> " + text);
				if(text.toLowerCase().contains("opening balance")){
					//System.out.println("starting transactions for the account -> " + account.getAccountNumber());
					String bal = text.replace("Opening Balance:", "").trim();
					bal = bal.replace(",", "");
					double balance = Double.parseDouble(bal);
					lastBal = balance;
					continue;
				}

				Matcher m = p1.matcher(text);
				String transDate = "";
				String desc = "";
				String amount = "";
				String runningBal = "";
				String transType = null;
				if(m.matches()){
					transFound = true;
					transDate = m.group(1);
					desc = m.group(2);
					amount = m.group(4);
					runningBal = m.group(7);
					runningBal = runningBal.replace(",", "");

					double balance = Double.parseDouble(runningBal);

					if(balance > lastBal){
						transType = BankTransaction.TRANSACTION_TYPE_CREDIT;
					}
					else{
						transType = BankTransaction.TRANSACTION_TYPE_DEBIT;
					}

					lastBal = balance;

					BankTransaction bt = new BankTransaction();
					bt.setTransDate(transDate);
					bt.setDescription(desc);
					bt.setTransactionType(transType);
					bt.setRunningBalance(runningBal);
					bt.setAccountNumber(account.getAccountNumber());
					bt.setCurrency(account.getCurrency());
					bt.setAmount(amount);
					account.addTransaction(bt);
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
					m = p2.matcher(text);
					if(m.matches()){
						transFound = true;
						transDate = m.group(1);
						desc = m.group(2);
						amount = m.group(4);

						amount = amount.replace(",", "");
						double tempAmount = Double.parseDouble(amount);
						double runBal = lastBal;

						if(text.contains("(DR)")){
							transType = BankTransaction.TRANSACTION_TYPE_DEBIT;
							runBal = lastBal - tempAmount;
							lastBal = runBal;
						}
						else{
							transType = BankTransaction.TRANSACTION_TYPE_CREDIT;
							runBal = lastBal + tempAmount;
							lastBal = runBal;
						}

						BankTransaction bt = new BankTransaction();
						bt.setTransDate(transDate);
						bt.setDescription(desc);
						bt.setTransactionType(transType);
						bt.setRunningBalance(String.format("%.2f", runBal));
						bt.setAccountNumber(account.getAccountNumber());
						bt.setCurrency(account.getCurrency());
						bt.setAmount(amount);
						account.addTransaction(bt);
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
						if(text.toUpperCase().contains("CLOSING BALANCE")){
							String balRegex = ".*CLOSING BALANCE ((\\d*,)*\\d+(\\.)\\d+) ((\\d*,)*\\d+(\\.)\\d+) ((\\d*,)*\\d+(\\.)\\d+).*";
							Pattern p = Pattern.compile(balRegex, Pattern.CASE_INSENSITIVE);
							m = p.matcher(text);
							if(m.matches()){
								account.setAccountBalance(m.group(7));
							}
							//System.out.println("transactions completed for the account -> " + account.getAccountNumber());
							break;
						}
						if(transFound){
							if(text.toLowerCase().contains("your citibank account") || text.toLowerCase().contains("page") 
									|| text.toLowerCase().contains("date transaction details") || lastTrans == null){
								//System.out.println("not a transaction. So Skipping.");
								continue;
							}
//							//System.out.println(">> more desc <<");
							if(text.contains("Statement Period")){
								text = text.substring(0, text.indexOf("Statement Period"));
							}
							desc = lastTrans.getDescription() + " " + text;
							lastTrans.setDescription(desc);

						}
						
					}
				}

			}
		}
		
		return response;
	}

}
