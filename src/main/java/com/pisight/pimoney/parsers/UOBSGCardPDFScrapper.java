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

import com.pisight.pimoney.beans.Response;
import com.pisight.pimoney.beans.CardAccount;
import com.pisight.pimoney.beans.CardTransaction;
import com.pisight.pimoney.beans.ParserUtility;

public class UOBSGCardPDFScrapper extends PDFParser {

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

			WebElement stmtEle = page.findElement(By.xpath("//td[contains(text(), 'Statement Date')]"));
			String stmtDate = stmtEle.getText().trim();
			stmtDate = stmtDate.substring(stmtDate.indexOf("Statement Date")+15).trim();
			
			WebElement minPayEle = page.findElement(By.xpath("//td[contains(text(), 'Minimum Payment Due')]"));
			String minPay = minPayEle.getText().trim();
			minPay = minPay.substring(minPay.indexOf("Minimum Payment Due")+20).trim();
			
			String currency = minPay.substring(0, 3).trim();
			minPay = minPay.replace(currency, "").trim();
			
			
			WebElement dueDateEle = page.findElement(By.xpath("//td[contains(text(), 'Payment Due Date')]"));
			String dueDate = dueDateEle.getText().trim();
			dueDate = dueDate.substring(dueDate.indexOf("Payment Due Date")+17).trim();
			
			WebElement totalLimitEle = page.findElement(By.xpath("//td[contains(text(), 'Total Credit Limit')]"));
			String totalLimit = totalLimitEle.getText().trim();
			totalLimit = totalLimit.substring(totalLimit.indexOf("Total Credit Limit")+19).trim();
			totalLimit = totalLimit.replace(currency, "");
			totalLimit = totalLimit.replace(",", "");
			
			double limit = Double.parseDouble(totalLimit);

			
			List<CardAccount> accounts  = new ArrayList<CardAccount>();

			List<WebElement> accountEle = page.findElements(By.xpath("//td[contains(text(), 'Credit Card(s) Statement')]/../following-sibling::tr"));

			String accountRegEx = "(.*) (\\d{4}.\\d{4}.\\d{4}.\\d{4}) (.*) ((\\d*,)?\\d+(\\.)\\d+) ((\\d*,)?\\d+(\\.)\\d+)";
			
			String accountEndRegEx = "((\\d*,)?\\d+(\\.)\\d+) ((\\d*,)?\\d+(\\.)\\d+)";

			Pattern pAccount = Pattern.compile(accountRegEx);
			Pattern pAccEnd = Pattern.compile(accountEndRegEx);

			Matcher m = null;
			boolean accountsFound = false;
			for(WebElement rowEle: accountEle){
				//System.out.println("1");
				String rowText = rowEle.getText().trim();

				m = pAccount.matcher(rowText);

				if(m.matches()){
					//System.out.println("2");

					if(!accountsFound){
						//System.out.println("3");
						accountsFound = true;
					}

					String accountNumber = m.group(2);
					String accountHolder = m.group(3);
					String balance = m.group(4);
					String minPayment = m.group(7);
					balance = balance.replace(",", "");
					
					double bal = Double.parseDouble(balance);
					double availableLimit = limit - bal;
					
					CardAccount ca = new CardAccount();

					ca.setAccountNumber(accountNumber);
					ca.setAmountDue(balance);
					ca.setMinAmountDue(minPayment);
					ca.setBillDate(stmtDate);
					ca.setDueDate(dueDate);
					ca.setTotalLimit(totalLimit);
					ca.setAvailableCredit(String.format("%.2f", availableLimit));
					ca.setAccountHolder(accountHolder);
					ca.setCurrency(currency);
					accounts.add(ca);
					response.addCardAccount(ca);
					//System.out.println();
					//System.out.println("Account Balance      ::: " + balance);
					//System.out.println("Account Number       ::: " + accountNumber);
					//System.out.println("Statement Date       ::: " + stmtDate);
					//System.out.println("available credit       ::: " + availableLimit);
					//System.out.println();
				}
				else{
					//System.out.println("4");

					if(accountsFound){
					
						m = pAccEnd.matcher(rowText);
						if(m.matches()){
							//System.out.println("All accounts scrapped. Now Skipping the loop.");
							break;
						}
					}
				}

			}

			for(CardAccount account:accounts){


				String identifier = account.getAccountNumber() + " " + account.getAccountHolder();
				String xpath = "//td[contains(text(), '" + identifier + "')]/../following-sibling::tr";

				List<WebElement> transEle = page.findElements(By.xpath(xpath));

				String transRegEx1 = "\\d{1,2} \\w{3} (\\d{1,2} \\w{3}) (.*) ((\\d*,)?\\d+(.)\\d+ ?(CR)?)";

				Pattern pTrans1 = Pattern.compile(transRegEx1);

				boolean transFound = false;
				//System.out.println("List size   ::: " + transEle.size());
				for(WebElement rowEle: transEle){

					String rowText = rowEle.getText().trim();
					//System.out.println("rowtext ::: " + rowText);

					Matcher m1 = pTrans1.matcher(rowText);
					String transDate = "";
					String desc = "";
					String amount = "";
					String transType = "";
					if(m1.matches()){
						transFound = true;
						//System.out.println(11);

						transDate = m1.group(1);
						desc = m1.group(2);
						amount = m1.group(3);

						//System.out.println(transDate + " :: " + desc + " :: " + amount);
						if(amount.toUpperCase().contains("CR")){
							transType = CardTransaction.TRANSACTION_TYPE_CREDIT;
						}
						else{
							transType = CardTransaction.TRANSACTION_TYPE_DEBIT;
						}
						amount = amount.replace("CR", "").trim();
						amount = amount.replace("Cr", "");
						transDate = ParserUtility.getYear(transDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM, 
								stmtDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM_SPACE_YYYY);

						CardTransaction ct = new CardTransaction();

						ct.setAmount(amount);
						ct.setDescription(desc);
						ct.setTransDate(transDate);
						ct.setTransactionType(transType);
						ct.setAccountNumber(account.getAccountNumber());
						ct.setCurrency(currency);
						account.addTransaction(ct);
						//System.out.println();
						//System.out.println();
						//System.out.println("Transaction Desc    ::: " + ct.getDescription());
						//System.out.println("Transaction amount  ::: " + ct.getAmount());
						//System.out.println("Transaction date    ::: " + ct.getTransDate());
						//System.out.println("Transaction type    ::: " + ct.getTransactionType());
						//System.out.println();
						//System.out.println();


					}
					else{
						//System.out.println(22);
						if(transFound){
							//System.out.println(33);
							if(rowText.contains("TOTAL BALANCE FOR")){
								//System.out.println("Transaction Ends for the account " + account.getAccountNumber());
								break;
							}
						}

					}
				}


			}
		 
		 return response;
	}

}
