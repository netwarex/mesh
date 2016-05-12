package com.gentics.mesh.core.rest.error;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Abstract class for regular rest exceptions. This class should be used when returning the exception information via a JSON response.
 */
@JsonIgnoreProperties({ "suppressed", "cause", "detailMessage", "stackTrace", "localizedMessage" })
public abstract class AbstractRestException extends RuntimeException {

	private static final long serialVersionUID = 2209919403583173663L;

	protected HttpResponseStatus status;
	protected String[] i18nParameters;
	protected String i18nMessage;

	public AbstractRestException() {
	}

	/**
	 * Create a new exception.
	 * 
	 * @param status
	 *            Status Code
	 * @param message
	 *            Message
	 * @param e
	 *            Underlying exception
	 */
	public AbstractRestException(HttpResponseStatus status, String message, Throwable e) {
		super(message, e);
		this.i18nMessage = message;
		this.status = status;
	}

	/**
	 * Create a new exception.
	 * 
	 * @param status
	 *            Status code
	 * @param message
	 *            Message
	 */
	public AbstractRestException(HttpResponseStatus status, String message) {
		super(message);
		this.i18nMessage = message;
		this.status = status;
	}

	/**
	 * Create a a new exception.
	 * 
	 * @param status
	 *            Http status
	 * @param i18nMessageKey
	 *            I18n message key
	 * @param i18nParameters
	 *            I18n parameters for the i18n message
	 */
	public AbstractRestException(HttpResponseStatus status, String i18nMessageKey, String... i18nParameters) {
		super(i18nMessageKey);
		this.status = status;
		this.i18nMessage = i18nMessageKey;
		this.i18nParameters = i18nParameters;
	}

	/**
	 * Create a new exception.
	 * 
	 * @param message
	 *            Message
	 */
	protected AbstractRestException(String message) {
		super(message);
		this.i18nMessage = message;
	}

	/**
	 * Return the http status code.
	 * 
	 * @return
	 */
	@JsonIgnore
	public HttpResponseStatus getStatus() {
		return status;
	}

	/**
	 * Set the nested http status code.
	 * 
	 * @param status
	 */
	public void setStatus(HttpResponseStatus status) {
		this.status = status;
	}

	/**
	 * Return the i18n parameters for the error message.
	 * 
	 * @return
	 */
	public String[] getI18nParameters() {
		return i18nParameters;
	}

	/**
	 * Returns the exception type. The type should be a human readable string which can be used to identify the error type.
	 * 
	 * @return
	 */
	public abstract String getType();

	@Override
	public String toString() {
		String extraInfo = "";
		if (getI18nParameters() != null) {
			extraInfo = " params {" + String.join(",", getI18nParameters()) + "}";
		}
		return getStatus() + " " + getMessage() + extraInfo;
	}

	@Override
	public String getMessage() {
		return i18nMessage;
	}

	/**
	 * Set the i18n message.
	 * 
	 * @param message
	 */
	public void setMessage(String message) {
		this.i18nMessage = message;
	}

}
