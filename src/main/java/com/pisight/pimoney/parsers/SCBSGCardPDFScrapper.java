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

public class SCBSGCardPDFScrapper extends PDFParser {

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

		WebElement dateEle = page.findElement(By.xpath("//td[contains(text(), 'Statement Date') and contains(text(), 'Payment Due Date')]"));
		String dates = dateEle.getText().trim();
		String dateRegEx = ".*Statement Date (\\d{1,2} \\w{3} \\d{4}).*Due.*(\\d{1,2} \\w{3} \\d{4})";
		Pattern p = Pattern.compile(dateRegEx);
		Matcher m = p.matcher(dates);
		String stmtDate = null;
		String dueDate = null;
		if(m.matches()){
			stmtDate = m.group(1);
			dueDate = m.group(2);
		}

		WebElement amountEle = page.findElement(By.xpath("//td[contains(text(), 'Approved Credit Limit Available Credit Limit')]/../following-sibling::tr[1]"));
		String amounts = amountEle.getText().trim();
		String amountRegEx = "((\\d*,)*\\d+(.)\\d+) ((\\d*,)*\\d+(.)\\d+) \\d{1,2} \\w{3} \\d{4} .*";
		p = Pattern.compile(amountRegEx);
		m = p.matcher(amounts);
		String totalLimit = null;
		String availableLimit = null;
		//System.out.println(amounts);
		if(m.matches()){
			//System.out.println("total limit");
			totalLimit = m.group(1);
			availableLimit = m.group(4);
		}
		List<CardAccount> accounts  = new ArrayList<CardAccount>();

		List<WebElement> accountEle = page.findElements(By.xpath("//td[contains(text(), 'Card Account New Balance')]/../following-sibling::tr"));

		String accountRegEx = ".*(\\d{4}.\\d{4}.\\d{4}.\\d{4}) ((\\d*,)?\\d+(.)\\d+) ((\\d*,)?\\d+(.)\\d+).*";

		Pattern pAccount = Pattern.compile(accountRegEx);

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

				String accountNumber = m.group(1);
				String balance = m.group(2);
				String minPayment = m.group(5);

				CardAccount ca = new CardAccount();

				ca.setAccountNumber(accountNumber);
				ca.setAmountDue(balance);
				ca.setMinAmountDue(minPayment);
				ca.setBillDate(stmtDate);
				ca.setDueDate(dueDate);
				ca.setTotalLimit(totalLimit);
				ca.setAvailableCredit(availableLimit);
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
					if(rowText.contains("Total")){
						//System.out.println("All accounts scrapped. Now Skipping the loop.");
						break;
					}
				}
			}

		}

		for(CardAccount account:accounts){


			String xpath = "//td[contains(text(), 'Date Description ')]/../following-sibling::tr";

			List<WebElement> transEle = page.findElements(By.xpath(xpath));

			String transRegEx1 = "(\\d{1,2} \\w{3}) \\d{1,2} \\w{3} (.*) ((\\d*,)?\\d+(.)\\d+ ?(CR)?)";
			String transStartRegEx = account.getAccountNumber() + " .*\\w{4,}.*";

			Pattern pTrans1 = Pattern.compile(transRegEx1);
			Pattern pTransStart = Pattern.compile(transStartRegEx, Pattern.CASE_INSENSITIVE);

			boolean transFound = false;
			//System.out.println("List size   ::: " + transEle.size());
			for(WebElement rowEle: transEle){

				String rowText = rowEle.getText().trim();
				//System.out.println("rowtext ::: " + rowText);
				if(!transFound){

					m = pTransStart.matcher(rowText);
					if(m.matches()){
						//System.out.println("Transaction starts for the account " + account.getAccountNumber());
						transFound = true;
					}
					continue;
				}

				Matcher m1 = pTrans1.matcher(rowText);
				String transDate = "";
				String desc = "";
				String amount = "";
				String transType = "";
				if(m1.matches()){
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
					amount = amount.replaceAll(",", "");
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
						if(rowText.contains("NEW BALANCE")){
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
