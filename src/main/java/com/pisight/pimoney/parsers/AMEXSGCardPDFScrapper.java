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
import com.pisight.pimoney.beans.ParserUtility;
import com.pisight.pimoney.beans.Response;

public class AMEXSGCardPDFScrapper extends PDFParser {

	@Override
	public Response parse(WebDriver driver, PDDocument pdDocument) throws Exception {
		// TODO Auto-generated method stub
		String page = parsePDFToHTML(pdDocument);

		JavascriptExecutor js = (JavascriptExecutor) driver;

		//System.out.println(page);

		js.executeScript(page);

		return scrapeStatement(driver);
	}

	private Response scrapeStatement(WebDriver driver) throws Exception{

		Response response = new Response();
		//System.out.println("#@#@#@#@##@#@##@#@#@##@#@#@#@#@##@#@#@#@#@#@##@#@#@#@#");
		//System.out.println();
		WebElement page = driver.findElement(By.id("PDF_TO_HTML"));

		String accountName = "American Express Intl Inc";
		WebElement branchEle = page.findElement(By.xpath("//td[contains(text(), 'American Express Intl Inc')]/../following-sibling::tr[1]"));
		String branchRow = branchEle.getText();
		branchRow = branchRow.replace("Statement of Account", "").trim();
		String branch = branchRow.substring(0, branchRow.indexOf(",")).trim();
		//System.out.println("Account Name:::: " + accountName);
		//System.out.println("Branch Name:::: " + branch);


		WebElement nameEle = page.findElement(By.xpath("//td[contains(text(), 'Prepared for')]/../following-sibling::tr[1]"));
		String nameRow = nameEle.getText();
		String name = nameRow.substring(0, nameRow.indexOf("xxxx")).trim();
		//System.out.println("Name:::: " + name);
		nameRow = nameRow.substring(nameRow.indexOf("xxxx")).trim();
		String membershipNo = nameRow.substring(0, nameRow.indexOf(" ")).trim();
		//System.out.println("Membership Number:::: " + membershipNo);
		String statementDate = nameRow.substring(nameRow.indexOf(" "), nameRow.indexOf("GST")).trim();
		//System.out.println("Statement Date:::: " + statementDate);

		WebElement balanceEle  = page.findElement(By.xpath("//td[contains(text(), 'Opening Balance')]/../following-sibling::tr[1]"));
		String balanceRow = balanceEle.getText();
		String lastStatementBalance = balanceRow.substring(0, balanceRow.indexOf(" ")).trim();
		//System.out.println("Last Statement Balance:::: " + lastStatementBalance);
		balanceRow = balanceRow.substring(balanceRow.indexOf("=")+1).trim();
		String amountDue = balanceRow.substring(0, balanceRow.indexOf(" ")).trim();
		//System.out.println("Amount Due:::: " + amountDue);
		String minAmountDue = balanceRow.substring(balanceRow.indexOf(" ")).trim();
		//System.out.println("Min Amount Due:::: " + minAmountDue);

		WebElement dueDateEle  = page.findElement(By.xpath("//td[contains(text(), 'Due Date')]/../following-sibling::tr[1]"));
		String dueDate = dueDateEle.getText().trim();
		//System.out.println("Due Date:::: " + dueDate);

		WebElement creditEle  = page.findElement(By.xpath("//td[contains(text(), 'Credit Summary Credit Limit')]/../following-sibling::tr[1]"));
		String creditRow = creditEle.getText().trim();
		String availableCredit = creditRow.substring(creditRow.lastIndexOf(" ")).trim();
		//System.out.println("Available Credit:::: " + availableCredit);
		creditRow = creditRow.substring(0, creditRow.lastIndexOf(" ")).trim();
		String creditLimit = creditRow.substring(creditRow.lastIndexOf(" ")).trim();
		//System.out.println("Credit Limit:::: " + creditLimit);

		WebElement membershipEle  = page.findElement(By.xpath("//td[contains(text(), 'Membership Number Please return this portion')]/../following-sibling::tr[1]"));
		String membershipRow = membershipEle.getText().trim();
		membershipRow = membershipRow.replace("PAYMENT ADVICE", "").trim();
		membershipNo = membershipRow.substring(0, membershipRow.indexOf(" ")).trim();
		//System.out.println("Membership Number:::: " + membershipNo);

		List<WebElement> transactionEles  = page.findElements(By.xpath("//td[contains(text(), 'Details Foreign Spending Amount')]/../following-sibling::tr"));

		CardAccount ca = new CardAccount();
		ca.setAccountHolder(name);
		ca.setBillDate(statementDate);
		ca.setLastStatementBalance(lastStatementBalance);
		ca.setAmountDue(amountDue);
		ca.setMinAmountDue(minAmountDue);
		ca.setDueDate(dueDate);
		ca.setAvailableCredit(availableCredit);
		ca.setTotalLimit(creditLimit);
		ca.setAccountNumber(membershipNo);
		ca.setBranch(branch);
		ca.setAccountName(accountName);
		response.addCardAccount(ca);

		CardTransaction lastTrans = null;
		String regex = "(\\w{3,9} \\d{1,2}) (.*) ((\\d*,)*\\d+(\\.)\\d+)";
		Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);

		for(WebElement ele: transactionEles){

			//System.out.println();
			String rowText = ele.getText().trim();

			
			if(rowText.equals("CR") && lastTrans != null){
				lastTrans.setTransactionType(CardTransaction.TRANSACTION_TYPE_CREDIT);
				continue;
			}
			if(!rowText.contains(".")){
				continue;
			}
			if(rowText.contains("Total of new transactions for")){
				break;
			}
			Matcher m = p.matcher(rowText);
			if(m.matches()){
				
				
				String transDate = "";
				String amount = "";
				String desc = "";
				CardTransaction ct = new CardTransaction();

				amount = rowText.substring(rowText.lastIndexOf(" ")).trim();
				rowText = rowText.substring(0, rowText.lastIndexOf(" ")).trim();
				rowText = rowText.substring(0, rowText.lastIndexOf(" ")).trim();
				transDate = rowText.substring(0, rowText.indexOf(" ", rowText.indexOf(" ")+1)).trim();
				desc = rowText.substring(rowText.indexOf(" ", rowText.indexOf(" ")+1)).trim();

				//System.out.println("  >> Transaction Date :: " + transDate);
				//System.out.println("  >> Amount           :: " + amount);
				//System.out.println("  >> Description      :: " + desc);

				transDate = formatDate(transDate, true);
				transDate = ParserUtility.getYear(transDate, ParserUtility.DATEFORMAT_MMMM_SPACE_DD, 
						formatDate(ca.getBillDate(), false), ParserUtility.DATEFORMAT_MMMM_SPACE_DD_COMMA_SPACE_YYYY);

				//System.out.println("  >> New Date         :: " + transDate);
				ct.setTransDate(transDate);
				ct.setAmount(amount);
				ct.setDescription(desc);
				ct.setTransactionType(CardTransaction.TRANSACTION_TYPE_DEBIT);
				ct.setAccountNumber(membershipNo);
				ca.addTransaction(ct);
				if(ca.getCurrency() != null && !ca.getCurrency().equals(" ")){
					ct.setCurrency(ca.getCurrency());
				}
				lastTrans = ct;
			}



		}

		return response;





	}

	private String formatDate(String date, boolean isTrans){

		String[] temp = date.trim().split(" ");
		if(isTrans){
			if(temp[1].length() == 1){
				date = temp[0] + " 0" + temp[1];
			}
		}
		else{
			if(temp[1].length() == 1){
				date = temp[0] + " 0" + temp[1] + " " + temp[2];
			}
		}
		return date;

	}


}
