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

import com.pisight.pimoney.beans.CardAccount;
import com.pisight.pimoney.beans.CardTransaction;
import com.pisight.pimoney.beans.ParserUtility;
import com.pisight.pimoney.beans.Response;

public class ANZSGCardPDFScrapper extends PDFParser {

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
		WebElement detailsEle = page.findElement(By.xpath("//td[contains(text(), 'STATEMENT DATE CREDIT LIMIT')]/../following-sibling::tr[1]"));

		String details = detailsEle.getText().trim();
		String detailsRegEx = "(\\d{1,2} \\w{3} \\d{4}) \\$((\\d*,)*\\d+(.)\\d+) \\$((\\d*,)*\\d+(.)\\d+) (\\d{1,2} \\w{3} \\d{4})";
		Pattern p = Pattern.compile(detailsRegEx);
		Matcher m = p.matcher(details);

		String stmtDate = null;
		String creditLimit = null;
		String minPayment = null;
		String dueDate = null;
		if(m.matches()){
			stmtDate = m.group(1);
			creditLimit = m.group(2);
			minPayment = m.group(5);
			dueDate = m.group(8);
		}
		else{
			throw new Exception("PDF format changed. Please verify the pdf and change the parser");
		}
		
		WebElement balanceEle = page.findElement(By.xpath("//td[contains(text(), 'GRAND TOTAL:')]"));
		
		String balance = balanceEle.getText().trim();
		
		balance = balance.replace("GRAND TOTAL:", "").trim();
		balance = balance.replace(",", "");
		
		creditLimit = creditLimit.replace(",", "");
		double bal = Double.parseDouble(balance);
		double totalLimit = Double.parseDouble(creditLimit);
		
		double availableCredit = totalLimit - bal;
		String avlCredit = String.format("%.2f", availableCredit);
		

		List<CardAccount> accounts  = new ArrayList<CardAccount>();

		WebElement accountEle = page.findElement(By.xpath("//td[contains(text(), 'DATE DESCRIPTION AMOUNT')]/../following-sibling::tr[1]"));


		String accountText = accountEle.getText().trim();

		String accountNumber = accountText.substring(accountText.length()-19);
		String accountName = accountText.replace(accountNumber,"");

		CardAccount ca = new CardAccount();

		ca.setAccountNumber(accountNumber);
		ca.setCurrency(currency);
		ca.setBillDate(stmtDate);
		ca.setAccountName(accountName);
		ca.setTotalLimit(creditLimit);
		ca.setDueDate(dueDate);
		ca.setMinAmountDue(minPayment);
		ca.setAmountDue(balance);
		ca.setAvailableCredit(avlCredit);
		accounts.add(ca);
		response.addCardAccount(ca);
		//System.out.println();
		//System.out.println("Account Currency     ::: " + currency);
		//System.out.println("Account Number       ::: " + accountNumber);
		//System.out.println("Statement Date       ::: " + stmtDate);
		//System.out.println("Account Name         ::: " + accountName);
		//System.out.println();


		for(CardAccount account:accounts){

			List<WebElement> transEle = page.findElements(By.xpath("//td[contains(text(), '" + account.getAccountName() + "') and "
					+ " contains(text(), '" + account.getAccountNumber() + "')]/../following-sibling::tr"));

			String transRegEx1 = "(\\d{1,2} \\w{3}) (.*) (\\(?(\\d*,)?\\d+(.)\\d+\\)?)";

			account.setAccountNumber(accountNumber);


			Pattern pTrans1 = Pattern.compile(transRegEx1);

			boolean transFound = false;

			//System.out.println("List size   ::: " + transEle.size());
			CardTransaction lastTrans = null;
			//just scraping second line of description in case of multi-line description
			boolean newDesc = false;
			for(WebElement rowEle: transEle){

				String rowText = rowEle.getText().trim();
				//System.out.println("rowtext ::: " + rowText);
				if(rowText.contains("PREVIOUS BALANCE")){

					if(!transFound){
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
					newDesc = true;
					transDate = m1.group(1);
					desc = m1.group(2);
					amount = m1.group(3);


					if(amount.contains("(")){
						transType = CardTransaction.TRANSACTION_TYPE_CREDIT;
					}
					else{
						transType = CardTransaction.TRANSACTION_TYPE_DEBIT;
					}
					amount = amount.replace("(", "");
					amount = amount.replace(")", "");

					transDate = ParserUtility.getYear(transDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM, 
							stmtDate, ParserUtility.DATEFORMAT_DD_SPACE_MMM_SPACE_YYYY);

					CardTransaction ct = new CardTransaction();

					ct.setAmount(amount);
					ct.setDescription(desc);
					ct.setTransDate(transDate);
					ct.setTransactionType(transType);
					ct.setCurrency(account.getCurrency());
					ct.setAccountNumber(accountNumber);
					account.addTransaction(ct);
					lastTrans = ct;
					//System.out.println();
					//System.out.println();
					//System.out.println("Transaction Desc    ::: " + lastTrans.getDescription());
					//System.out.println("Transaction amount  ::: " + lastTrans.getAmount());
					//System.out.println("Transaction date    ::: " + lastTrans.getTransDate());
					//System.out.println("Transaction type    ::: " + lastTrans.getTransactionType());
					//System.out.println("Transaction currency::: " + lastTrans.getCurrency());
					//System.out.println();
					//System.out.println();


				}
				else{
					//System.out.println(22);
					if(transFound){
						//System.out.println(33);
						if(rowText.contains("SUB-TOTAL:")){
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
		
		return response;
	}

}
