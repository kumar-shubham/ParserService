package com.pisight.pimoney.beans;

import java.util.ArrayList;
import java.util.List;

public class Response {
	
	private List<BankAccount> bankAccounts = new ArrayList<BankAccount>();
	
	private List<CardAccount> cardAccounts = new ArrayList<CardAccount>();
	
	private boolean isEncrypted = false;
	
	private boolean isPswdCorrect = false;

	
	
	
	public boolean isEncrypted() {
		return isEncrypted;
	}

	public void setEncrypted(boolean isEncrypted) {
		this.isEncrypted = isEncrypted;
	}

	public boolean isPswdCorrect() {
		return isPswdCorrect;
	}

	public void setPswdCorrect(boolean isPswdCorrect) {
		this.isPswdCorrect = isPswdCorrect;
	}

	public List<BankAccount> getBankAccounts() {
		return bankAccounts;
	}

	public void addBankAccount(BankAccount bankAccount) {
		bankAccounts.add(bankAccount);
	}

	public List<CardAccount> getCardAccounts() {
		return cardAccounts;
	}

	public void addCardAccount(CardAccount cardAccount) {
		cardAccounts.add(cardAccount);
	}
	
	

}
