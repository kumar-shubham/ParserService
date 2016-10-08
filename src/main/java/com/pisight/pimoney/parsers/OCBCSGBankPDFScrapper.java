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

public class OCBCSGBankPDFScrapper extends PDFParser {

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

		WebElement currencyEle = page.findElement(By.xpath("//td[contains(text(), 'Currency')]"));

		String currency = currencyEle.getText().trim();
		currency = currency.substring(currency.indexOf("Currency")+8).trim();


		WebElement stmtDateEle = page.findElement(By.xpath("//td[contains(text(), 'INFORMATION AT A GLANCE')]/../preceding-sibling::tr[1]"));

		String stmtDate = stmtDateEle.getText().trim();
		List<BankAccount> accounts  = new ArrayList<BankAccount>();

		List<WebElement> accountEle = page.findElements(By.xpath("//td[contains(text(), 'INFORMATION AT A GLANCE')]/../following-sibling::tr"));

		String accountRegEx = "(.*) (\\d{12}) ((\\d*,)?\\d+(.)\\d+)";

		Pattern pAccount = Pattern.compile(accountRegEx);

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
				ba.setCurrency(currency);
				ba.setBillDate(stmtDate);
				ba.setAccountName(accountName);
				accounts.add(ba);
				response.addBankAccount(ba);
				//System.out.println();
				//System.out.println("Account Balance      ::: " + balance);
				//System.out.println("Account Currency     ::: " + currency);
				//System.out.println("Account Number       ::: " + accountNumber);
				//System.out.println("Statement Date       ::: " + stmtDate);
				//System.out.println();
			}
			else{
				//System.out.println("4");

				if(accountsFound){


					if(rowText.contains("Your Total")){
						//System.out.println("All accounts scrapped. Now Skipping the loop.");
						break;
					}
				}
			}

		}

		for(BankAccount account:accounts){

			String identifier = "Account No. " + account.getAccountNumber();

			//System.out.println("identifier  ::: " + identifier);

			List<WebElement> transEle = page.findElements(By.xpath("//td[contains(text(), '" + identifier + "')]/../following-sibling::tr"));

			String transRegEx1 = "(\\d{2} \\w{3}) (\\d{2} \\w{3}) (.*) ((\\d*,)?\\d+(.)\\d+) ((\\d*,)?\\d+(.)\\d+)";
			String transEndRegEx = "Total Withdrawals/Deposits ((\\d*,)?\\d+(.)\\d+) ((\\d*,)?\\d+(.)\\d+)";

			Pattern pTrans1 = Pattern.compile(transRegEx1);
			Pattern pTransEnd = Pattern.compile(transEndRegEx);

			boolean transFound = false;

			//System.out.println("List size   ::: " + transEle.size());
			double lastBal = Integer.MIN_VALUE;
			BankTransaction lastTrans = null;
			for(WebElement rowEle: transEle){

				String rowText = rowEle.getText().trim();
				//System.out.println("rowtext ::: " + rowText);
				if(rowText.contains("BALANCE B/F")){

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
				String transDate = "";
				String postDate = "";
				String desc = "";
				String amount = "";
				String runningBalance = "";
				String transType = "";
				if(m1.matches()){
					//System.out.println(11);

					if(lastTrans != null && lastTrans.getTransactionType().equals("")){
						//System.out.println("Couldn't catagorize the transactions. So setting it as Debit");
						lastTrans.setTransactionType(BankTransaction.TRANSACTION_TYPE_DEBIT);

					}
					transDate = m1.group(1);
					postDate = m1.group(2);
					desc = m1.group(3);
					amount = m1.group(4);
					runningBalance = m1.group(7);
					runningBalance = runningBalance.replaceAll(",", "");
					runningBalance = runningBalance.replace("Cr", "").trim();

					double runBal = Double.parseDouble(runningBalance);

					if(runBal>lastBal){
						transType = BankTransaction.TRANSACTION_TYPE_CREDIT;
					}
					else{
						transType = BankTransaction.TRANSACTION_TYPE_DEBIT;
					}
					lastBal = runBal;


					transDate = getCorrectYear(transDate, postDate, stmtDate);
					
					BankTransaction bt = new BankTransaction();

					bt.setAmount(amount);
					bt.setDescription(desc);
					bt.setTransDate(transDate);
					bt.setRunningBalance(runningBalance);
					bt.setTransactionType(transType);
					bt.setCurrency(account.getCurrency());
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
					if(transFound){
						//System.out.println(33);
						if(rowText.contains("BALANCE C/F")){
							//System.out.println("Transactions completed for this page. Moving to next page.");
							continue;
						}
						m1 = pTransEnd.matcher(rowText);

						if(m1.matches()){
							//System.out.println("Transaction Ends for the account " + account.getAccountNumber());
							break;
						}
						else{
							lastTrans.setDescription(lastTrans.getDescription() + " " + rowText);
						}
					}

				}
			}


		}
		
		return response;
	}
	
	private static String getCorrectYear(String date1, String date2, String refDate) throws Exception{
		
		String result = ParserUtility.getYear(date1, ParserUtility.DATEFORMAT_DD_SPACE_MMM, 
				refDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM_SPACE_YYYY);
		//System.out.println("date 1 -> " + date1 + " date 2 -> " + date2);
		if(!date1.equals(date2)){
			//System.out.println("unequal trans and post date");
			date2 = ParserUtility.getYear(date2, ParserUtility.DATEFORMAT_DD_SPACE_MMM, 
					refDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM_SPACE_YYYY);
			
			int year1 = Integer.parseInt(result.substring(result.length()-4));
			int year2 = Integer.parseInt(date2.substring(date2.length()-4));
			
			if(year1 < year2){
				//System.out.println("date mismatch while parsing the transaction date. So adding correct year");
				result = date1.trim() + " " + year2;
			}
		}
		
		return result;
	}

}
