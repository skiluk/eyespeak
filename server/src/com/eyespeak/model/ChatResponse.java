package com.eyespeak.model;

import java.util.ArrayList;

public class ChatResponse {
	public long utteranceId;
	public String utteranceText;
	public ArrayList<Option> responses;

	public ChatResponse() {
		utteranceId = -1;
		responses = new ArrayList<>();
	}
}
