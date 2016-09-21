package com.pisight.pimoney.beans;

import java.util.ArrayList;
import java.util.List;

public class Response {
	
	private List<BankAccount> bankAccounts = new ArrayList<BankAccount>();
	
	private List<CardAccount> cardAccounts = new ArrayList<CardAccount>();

	
	
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
