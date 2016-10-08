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

public class SCBSGBankPDFScrapper extends PDFParser {

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

		String currency = "SGD";
		WebElement stmtDateEle = page.findElement(By.xpath("//td[contains(text(), 'Statement Date:')]"));

		String stmtDate = stmtDateEle.getText().trim();
		stmtDate = stmtDate.replace("Statement Date:", "").trim();
		stmtDate = stmtDate.substring(0, 11);
		List<BankAccount> accounts  = new ArrayList<BankAccount>();

		List<WebElement> accountEle = page.findElements(By.xpath("//td[contains(text(), 'YOUR ACCOUNT BALANCES')]/../following-sibling::tr"));

		String accountRegEx = "# (.*) (\\d{2}.\\d.\\d{6}.\\d) ((\\d*,)?\\d+(.)\\d+)";

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
				//System.out.println("Account Name         ::: " + accountName);
				//System.out.println();
			}
			else{
				//System.out.println("4");

				if(accountsFound){


					if(rowText.contains("YOUR ACCOUNT ACTIVITIES")){
						//System.out.println("All accounts scrapped. Now Skipping the loop.");
						break;
					}
				}
			}

		}

		for(BankAccount account:accounts){

			List<WebElement> transEle = page.findElements(By.xpath("//td[contains(text(), '" + account.getAccountName() + "') and "
					+ " contains(text(), '" + account.getAccountNumber() + "') and not(contains(text(), '#'))]/../following-sibling::tr"));

			String transRegEx1 = "(\\d{2} \\w{3} )?(.*) ((\\d*,)?\\d+(.)\\d+) ((\\d*,)?\\d+(.)\\d+)";
			
			String accountNumber = account.getAccountNumber().replace("−", "");
			account.setAccountNumber(accountNumber);
		

			Pattern pTrans1 = Pattern.compile(transRegEx1);

			boolean transFound = false;

			//System.out.println("List size   ::: " + transEle.size());
			double lastBal = Integer.MIN_VALUE;
			BankTransaction lastTrans = null;
			String lastDate = null;
			//just scraping second line of description in case of multi-line description
			boolean newDesc = false;
			for(WebElement rowEle: transEle){

				String rowText = rowEle.getText().trim();
				//System.out.println("rowtext ::: " + rowText);
				if(rowText.contains("BALANCE FROM PREVIOUS STATEMENT")){

					if(!transFound){
						//System.out.println("Transaction starts for the account " + account.getAccountNumber());
						transFound = true;
					}
					String tempBal = rowText.substring(rowText.lastIndexOf(" ")).trim();
					tempBal = tempBal.replace(",", "");

					lastBal = Double.parseDouble(tempBal);
					lastDate = rowText.substring(0, 6);
					continue;
				}

				Matcher m1 = pTrans1.matcher(rowText);
				String transDate = "";
				String desc = "";
				String amount = "";
				String runningBalance = "";
				String transType = "";
				if(m1.matches()){
					//System.out.println(11);
					newDesc = true;
					if(m1.group(1) == null){
						transDate = lastDate;
					}
					else{
						transDate = m1.group(1);
						lastDate = transDate;
					}
					transDate = ParserUtility.getYear(transDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM, 
									stmtDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM_SPACE_YYYY);
					
					desc = m1.group(2);
					amount = m1.group(3);
					runningBalance = m1.group(6);
					runningBalance = runningBalance.replaceAll(",", "");

					double runBal = Double.parseDouble(runningBalance);

					if(runBal>lastBal){
						transType = BankTransaction.TRANSACTION_TYPE_CREDIT;
					}
					else{
						transType = BankTransaction.TRANSACTION_TYPE_DEBIT;
					}
					lastBal = runBal;


					//transDate = getCorrectYear(transDate, postDate, stmtDate);
					
					BankTransaction bt = new BankTransaction();

					bt.setAmount(amount);
					bt.setDescription(desc);
					bt.setTransDate(transDate);
					bt.setRunningBalance(runningBalance);
					bt.setTransactionType(transType);
					bt.setCurrency(account.getCurrency());
					bt.setAccountNumber(accountNumber);
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
						if(rowText.contains("CLOSING BALANCE")){
							//System.out.println("Transaction Ends for the account " + account.getAccountNumber());
							break;
						}
						else{
							if(newDesc){
								lastTrans.setDescription(lastTrans.getDescription() + " " + rowText);
								newDesc = false;
							}
						}
					}

				}
			}


		}
		
		return  response;
	}

}
