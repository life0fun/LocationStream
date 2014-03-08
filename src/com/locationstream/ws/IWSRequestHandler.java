
package com.locationstream.ws;


public interface IWSRequestHandler
{
	public interface WSRequestNotifyType
	{
		/**
		 * notifyOfResponse() - called when ResponseLevel is either ResponseLevel.ITEM/CHUNK
		 * and you have a response to give somebody while in the middle of processing their request
		 *  
		 * @param resp	- the response that you want to give to somebody
		 *
		 * @return true		- keep processing the request
		 * @return false 	- stop processing this request 
		 */
		boolean notifyOfResponse(WSResponse resp);
	}
	
	/**
	 * doRequest() - given a request return a response to it.  The returned response depends on the response level.
	 * 
	 * If responseLevel is ResponseLevel.ALL then the response contains the entire response for this request.
	 * If responseLevel is ResponseLevel.ITEM/CHUNK then the response contains the *last* response for this request.  
	 * This is usually the empty response (null data) and with the error ResponseFinishedError to indicate this request
	 * is finished.  The WSRequestNotifyType is only used when the responseLevel is ResponseLevel.ITEM/CHUNK and it
	 * provides a way to notify the request originator of responses in the middle of processing the request. 
	 * As an example here's how a simple doRequest method might be implemented;
	 * 
	 * WSResponse doRequest(WSRequest req, WSRequestNotifyType rnt)
	 * {
	 * 		WSResponse resp = null;
	 * 
	 * 		if (req.getResponseLevel() == ResponseLevel.ALL) {
	 * 			resp = req.createResponse(STATUS_CODE, getDataSomehow());
	 * 		} else {
	 * 			boolean cancelled = false;
	 * 
	 * 			while (!cancelled) {
	 * 				resp = req.createResponse(STATUS_CODE, getDataSomehow());
	 * 				cancelled = !rnt.notifyOfResponse(resp);
	 *  		}
	 * 	
	 * 			resp = req.createResponse(STATUS_CODE, null);
	 * 			if (cancelled) {	
	 * 				resp.setError(ErrorCodes.CancelledError);
	 * 			} else {
	 * 				resp.setError(ErrorCodes.ResponseFinishedError);
	 * 			}
	 * 		}
	 * 
	 * 		return resp;
	 * }
	 * 
	 * @param req	- the request to perform
	 * @param rnt	- how to notify the request originator of responses in the middle of processing the request 
	 * 				(only used when responseLevel is ITEM/CHUNK)
	 * 
	 * @return		- the resulting response for the supplied request.
	 */
	WSResponse doRequest(WSRequest req, WSRequestNotifyType rnt);
}