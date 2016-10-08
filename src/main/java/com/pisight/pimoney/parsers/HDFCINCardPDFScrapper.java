package com.pisight.pimoney.parsers;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import com.pisight.pimoney.beans.CardAccount;
import com.pisight.pimoney.beans.CardTransaction;
import com.pisight.pimoney.beans.Response;

public class HDFCINCardPDFScrapper extends PDFParser {

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

		String currency = "INR";
		List<WebElement> detailsEle = page.findElements(By.xpath("//td[contains(text(), 'Payment Due Date Total Dues ')]/../following-sibling::tr"));

		String stmtDate = null;
		String creditLimit = null;
		String minPayment = null;
		String dueDate = null;
		String accountNumber = null;
		String amountdue = null;
		String availableCredit = null;
		String detailsRegEx = ".*(\\d{2}.\\d{2}.\\d{4}) ((\\d*,)*\\d+(.)\\d+) ((\\d*,)*\\d+(.)\\d+).*";
		Pattern p = Pattern.compile(detailsRegEx);
		Matcher m = null;
		for(WebElement ele: detailsEle){
			String details = ele.getText().trim();
			m = p.matcher(details);
			if(m.matches()){
				dueDate = m.group(1);
				amountdue = m.group(2);
				minPayment = m.group(5);
				break;
			}
		}

		if(dueDate == null){
			throw new Exception("PDF format changed. Please verify the pdf and change the parser");
		}

		WebElement stmtEle = page.findElement(By.xpath("//td[contains(text(), 'Statement Date') and contains(text(), 'Card No')]"));
		String stmtText = stmtEle.getText().trim();
		String stmtRegex = "Statement Date.*(\\d{2}.\\d{2}.\\d{4}) .*Card No.*([\\d\\w ]{19})";
		p = Pattern.compile(stmtRegex, Pattern.CASE_INSENSITIVE);
		m = p.matcher(stmtText);
		if(m.matches()){
			stmtDate = m.group(1);
			accountNumber = m.group(2);
		}


		WebElement balEle = page.findElement(By.xpath("//td[contains(text(), 'Credit Limit Available Credit')]/../following-sibling::tr[1]"));
		String balText = balEle.getText().trim();
		String balRegEx = "((\\d*,)*\\d+(.)\\d+) ((\\d*,)*\\d+(.)\\d+) ((\\d*,)*\\d+(.)\\d+)";
		p = Pattern.compile(balRegEx);
		m = p.matcher(balText);

		if(m.matches()){
			creditLimit = m.group(1);
			availableCredit = m.group(4);
		}
		else{
			throw new Exception("PDF format changed 1.  Please verify the pdf and change the parser");
		}


		CardAccount ca = new CardAccount();

		ca.setAccountNumber(accountNumber);
		ca.setCurrency(currency);
		ca.setBillDate(stmtDate);
		ca.setTotalLimit(creditLimit);
		ca.setDueDate(dueDate);
		ca.setMinAmountDue(minPayment);
		ca.setAmountDue(amountdue);
		ca.setAvailableCredit(availableCredit);
		response.addCardAccount(ca);
		//System.out.println();
		//System.out.println("Account Currency     ::: " + currency);
		//System.out.println("Account Number       ::: " + accountNumber);
		//System.out.println("Statement Date       ::: " + stmtDate);
		//System.out.println();


		List<WebElement> transEle = page.findElements(By.xpath("//td[contains(text(), 'Date') and contains(text(), "
				+ "'Transaction') and contains(text(), 'Description')]/../following-sibling::tr"));

		String transRegEx1 = "(\\d{1,2}.\\d{2}.\\d{4}) (.*) ((\\d*,)*\\d+(.)\\d+( Cr)?)";

		Pattern pTrans1 = Pattern.compile(transRegEx1, Pattern.CASE_INSENSITIVE);

		boolean transFound = false;

		//System.out.println("List size   ::: " + transEle.size());
		for(WebElement rowEle: transEle){

			String rowText = rowEle.getText().trim();
			//System.out.println("rowtext ::: " + rowText);
			if(rowText.toUpperCase().contains("PREVIOUS BALANCE")){

				if(!transFound){
					//System.out.println("Transaction starts for the account " + ca.getAccountNumber());
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
				transFound = true;
				//System.out.println(11);
				transDate = m1.group(1);
				desc = m1.group(2);
				amount = m1.group(3);

				if(amount.toLowerCase().contains("cr")){
					transType = CardTransaction.TRANSACTION_TYPE_CREDIT;
				}
				else{
					transType = CardTransaction.TRANSACTION_TYPE_DEBIT;
				}
				amount = amount.replace("Cr", "");
				amount = amount.replace("CR", "");

				CardTransaction ct = new CardTransaction();

				ct.setAmount(amount);
				ct.setDescription(desc);
				ct.setTransDate(transDate);
				ct.setTransactionType(transType);
				ct.setCurrency(ca.getCurrency());
				ct.setAccountNumber(accountNumber);
				ca.addTransaction(ct);
				//System.out.println();
				//System.out.println();
				//System.out.println("Transaction Desc    ::: " + ct.getDescription());
				//System.out.println("Transaction amount  ::: " + ct.getAmount());
				//System.out.println("Transaction date    ::: " + ct.getTransDate());
				//System.out.println("Transaction type    ::: " + ct.getTransactionType());
				//System.out.println("Transaction currency::: " + ct.getCurrency());
				//System.out.println();
				//System.out.println();


			}
			else{
				//System.out.println(22);
				if(transFound){
					//System.out.println(33);
					if(rowText.contains("Page")){
						//System.out.println("Transactions scraped from the page. Now moving to next page.");
						transFound = false;
						continue;
					}
					if(rowText.toLowerCase().contains("opening balance") && rowText.toLowerCase().contains("closing bal")){
						//System.out.println("Transaction Ends for the account " + ca.getAccountNumber());
						break;
					}
				}

			}
		}
		
		return response;
	}

}
