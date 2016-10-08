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
import com.pisight.pimoney.beans.ParserUtility;
import com.pisight.pimoney.beans.Response;

public class DBSSGBankPDFScrapper extends PDFParser {
	
	
	@Override
	public Response parse(WebDriver driver, PDDocument pdDocument) throws Exception {
		// TODO Auto-generated method stub
//		String page = parsePDFToHTML(pdDocument);
		
		//calling overloaded method of pdfextrator for parsing transactions line correctly
		//arguments -> regex to match(in this case it is amount), starttext(usually withdrawal/debit in table header...
		//...end text(end of withdrawal column, can be null), markertext(text to be added for identification...
		//...halttext(the function should stop adding markertext after it meets halttext
		
		String page = parsePDFToHTML(pdDocument, "((\\d*,)*\\d+(\\.)\\d+)", "Withdrawal", null, "(DR)", "Balance Carried Forward", "Description");

		JavascriptExecutor js = (JavascriptExecutor) driver;

		//System.out.println(page);

		js.executeScript(page);

		return scrapeStatement(driver);
	}

	private Response scrapeStatement(WebDriver driver) throws Exception {
		// TODO Auto-generated method stub
		Response response  = new Response();
		
		//System.out.println("#@#@#@#@##@#@##@#@#@##@#@#@#@#@##@#@#@#@#@#@##@#@#@#@#");
		//System.out.println();
		WebElement page = driver.findElement(By.id("PDF_TO_HTML"));


		WebElement currencyEle = page.findElement(By.xpath("//td[contains(text(), 'Account Account Number (')]"));

		String currency = currencyEle.getText().trim();
		if(currency.contains("S$")){
			currency = "SGD";
		}
		else{
			currency = "";
		}

		WebElement stmtDateEle = page.findElement(By.xpath("//td[contains(text(), 'ACCOUNT SUMMARY') and contains(text(), 'As at')]"));

		String stmtDate = stmtDateEle.getText().trim();
		stmtDate = stmtDate.substring(stmtDate.indexOf("As at ")+5).trim();

		List<BankAccount> accounts  = new ArrayList<BankAccount>();

		List<WebElement> accountEle = page.findElements(By.xpath("//td[contains(text(), 'Account Account Number')]/../following-sibling::tr"));

		String accountRegEx = "(.*) (\\d{3}-\\d-\\d{6}) ((\\d*,)*\\d+(.)\\d+)";
		String accountEndRegEx = "TOTAL.* ((\\d*,)*\\d+(.)\\d+)";

		Pattern pAccount = Pattern.compile(accountRegEx);
		Pattern pAccountEnd = Pattern.compile(accountEndRegEx);

		boolean accountsFound = false;
		for(WebElement rowEle: accountEle){
			//System.out.println("1");
			String rowText = rowEle.getText().trim();

			Matcher m = pAccount.matcher(rowText);

			if(m.matches()){
				//System.out.println("2");

				if(!accountsFound){
					//System.out.println("3");
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
				//System.out.println();
				//System.out.println("Account Balance      ::: " + balance);
				//System.out.println("Account Currency     ::: " + currency);
				//System.out.println("Account Number       ::: " + accountNumber);
				//System.out.println("Account Name         ::: " + accountName);
				//System.out.println("Statement Date       ::: " + stmtDate);
				//System.out.println();
			}
			else{
				//System.out.println("4");

				if(accountsFound){

					m = pAccountEnd.matcher(rowText);

					if(m.matches()){
						//System.out.println("All accounts scrapped. Now Skipping the loop.");
						break;
					}
				}
			}

		}

		for(BankAccount account:accounts){

			String identifier = account.getAccountName() + " Account No. " + account.getAccountNumber();

			//System.out.println("identifier  ::: " + identifier);

			List<WebElement> transEle = page.findElements(By.xpath("//td[contains(text(), '" + identifier + "')]/../following-sibling::tr"));

			String transRegEx1 = "(\\d{2} \\w{3}) (.*) (\\(DR\\))?((\\d*,)*\\d+(.)\\d+).*( (\\d*,)*\\d+(.)\\d+)";
			String transRegEx2 = "(\\d{2} \\w{3}) (.*) (\\(DR\\))?((\\d*,)*\\d+(.)\\d+)(.*)";
			String transEndRegEx = "Total (\\(DR\\))?((\\d*,)*\\d+(.)\\d+) ((\\d*,)*\\d+(.)\\d+)";

			Pattern pTrans1 = Pattern.compile(transRegEx1,Pattern.CASE_INSENSITIVE);
			Pattern pTrans2 = Pattern.compile(transRegEx2,Pattern.CASE_INSENSITIVE);
			Pattern pTransEnd = Pattern.compile(transEndRegEx,Pattern.CASE_INSENSITIVE);

			boolean transFound = false;

			//System.out.println("List size   ::: " + transEle.size());
			double lastBal = Integer.MIN_VALUE;
			BankTransaction lastTrans = null;
			for(WebElement rowEle: transEle){

				String rowText = rowEle.getText().trim();
				//System.out.println("rowtext ::: " + rowText);
				if(rowText.contains("Balance Brought Forward")){

					if(!transFound){
						//System.out.println("Transaction starts for the account " + account.getAccountNumber());
						transFound = true;
					}
					String tempBal = rowText.substring(rowText.lastIndexOf(" ")).trim();
					tempBal = tempBal.replace(",", "");

					lastBal = Double.parseDouble(tempBal);
					continue;
				}

				Matcher m1 = pTrans1.matcher(rowText);
				Matcher m2 = pTrans2.matcher(rowText);
				String transDate = "";
				String desc = "";
				String amount = "";
				String runningBalance = "";
				String transType = "";
				if(m1.matches()){
					//System.out.println(11);

					transDate = m1.group(1);
					desc = m1.group(2);
					amount = m1.group(4);
					runningBalance = m1.group(7);
					runningBalance = runningBalance.replaceAll(",", "");

					transDate = ParserUtility.getYear(transDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM, 
							stmtDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM_SPACE_YYYY);

					double runBal = Double.parseDouble(runningBalance);

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
				else if(m2.matches()){
					//System.out.println(12);
					transDate = m2.group(1);
					desc = m2.group(2);
					amount = m2.group(4);
					String temp = m2.group(7);

					amount = amount.replace(",", "");

					double tempAmount = Double.parseDouble(amount);

					double runBal = lastBal;

					if(rowText.contains("(DR)") && !temp.contains("-")){
						//System.out.println("type -> debit");
						transType = BankTransaction.TRANSACTION_TYPE_DEBIT;
						runBal = lastBal - tempAmount;
						lastBal = runBal;
					}
					else{
						//System.out.println("type -> credit");
						transType = BankTransaction.TRANSACTION_TYPE_CREDIT;
						runBal = lastBal + tempAmount;
						lastBal = runBal;
					}

					transDate = ParserUtility.getYear(transDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM, 
							stmtDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM_SPACE_YYYY);


					BankTransaction bt = new BankTransaction();

					bt.setAmount(amount);
					bt.setDescription(desc);
					bt.setTransDate(transDate);
					bt.setRunningBalance(String.format("%.2f", runBal));
					bt.setTransactionType(transType);
					bt.setCurrency(currency);
					bt.setAccountNumber(account.getAccountNumber());
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
					//System.out.println(22);
					if(rowText.contains("Balance Carried Forward")){
						//System.out.println("Page completed. Moving to next page.");
						transFound = false;
						continue;
					}
					if(transFound){
						//System.out.println(33);
						m1 = pTransEnd.matcher(rowText);

						if(m1.matches()){
							//System.out.println("Transaction Ends for the account " + account.getAccountNumber());
							break;
						}
						else{
							//System.out.println(44);
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
