package plugins.XMLLibrarian.interfaces;

import freenet.pluginmanager.PluginHTTPException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;
import freenet.support.HTMLEncoder;
import freenet.l10n.L10n;

import freenet.support.Logger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import plugins.XMLLibrarian.Index;
import plugins.XMLLibrarian.InvalidSearchException;
import plugins.XMLLibrarian.Request;
import plugins.XMLLibrarian.Search;
import plugins.XMLLibrarian.URIWrapper;
import plugins.XMLLibrarian.XMLLibrarian;


/**
 * Provides the HTML generation for the search page
 * @author MikeB
 */
public class WebUI{
	static String plugName;
	static XMLLibrarian xl;
	
	public static void setup(XMLLibrarian xl, String plugName){
		WebUI.plugName = plugName;
		WebUI.xl = xl;
	}


	/**
	 * Decide what to do depending on the request
	 * @param request
	 * @return String of generated HTML to be sent to browser
	 * @throws freenet.pluginmanager.PluginHTTPException
	 */
	public static String handleHTTPGet(HTTPRequest request) throws PluginHTTPException{
		String searchstring = request.getParam("search");
		String indexuri = request.isParameterSet("index") ? request.getParam("index") : XMLLibrarian.DEFAULT_INDEX_SITE;
		
		if(request.getPath().endsWith("xml")) // mini progress display for use with JS
				return progressxml(searchstring,indexuri, "on".equals(request.getParam("showold")));
		
//		}else if(request.getPath().endsWith("result")){ // just get result for JS
//			if (Search.hasSearch(searchstring, indexuri)){
//				return Search.getSearch(searchstring, indexuri).getresult();
//			}else return "No asyncronous search for "+HTMLEncoder.encode(searchstring)+" found.";

		if(request.getPath().endsWith("debug")){
			return WebUI.debugpage();
		}
		if(request.getPath().endsWith("purgeSearches")){
			Search.purgeSearches();
			return WebUI.searchpage(indexuri, request.isParameterSet("js"));
		}
		if(request.getPath().endsWith("listSearches"))
			return WebUI.listSearches();

		if(searchstring == null || searchstring.equals("")){   // no search main
			// generate HTML and set it to no refresh
			return WebUI.searchpage(indexuri, request.isParameterSet("js"));

		}else{  // Full main searchpage
			Search searchobject = null;

			try{
				//get Search object
				searchobject = Search.startSearch(searchstring, indexuri);

				// generate HTML for search object and set it to refresh
				return searchpage(searchobject, indexuri, true, request.isParameterSet("js"), "on".equals(request.getParam("showold")), null);
			}catch(Exception e){
				return searchpage(searchobject, indexuri, false, false, false, e);
			}
		}
	}
    
	public static String handleHTTPPost(HTTPRequest request) throws PluginHTTPException{
        return searchpage(null, false);
    }



	/**
	 * Build an empty search page with no refresh
	 * @param indexuri an index to put in the index box
	 **/
	public static String searchpage(String indexuri, boolean js){
		return searchpage(null, indexuri, false, js, false, null);
	}
	
	
    /**
     * Build a search page for search in it's current state
	 * @param request the request this page should be built to show the progress of
	 * @param indexuri the index to show in the index box
	 * @param refresh a preference as to whether this page should refresh, refresh is switched off in the event of an error or the request being finished
	 * @param e any exception which should be reported on the page
     **/
    public static String searchpage(Search request, String indexuri, boolean refresh, boolean js, boolean showold, Exception e){
		if(request==null || "".equals(request.getQuery()) || request.isFinished() || e!=null)
			refresh = false;
			
		
        // Show any errors
		HTMLNode errorDiv = new HTMLNode("div", "id", "errors");
        if (e != null){
            addError(errorDiv, e);
		}
		if (request != null && request.getRequestStatus() == Request.RequestStatus.ERROR)
			addError(errorDiv, request.getError());
			
		String search = "";
		try{
			search = request !=null ? HTMLEncoder.encode(request.getQuery()) : "";
			if(indexuri == null || indexuri.equals(""))
				indexuri = request !=null  ? HTMLEncoder.encode(request.getIndexURI()) : XMLLibrarian.DEFAULT_INDEX_SITE;
		}catch(Exception exe){
			addError(errorDiv, exe);
		}
			
			
		HTMLNode pageNode = new HTMLNode.HTMLDoctype("html", "-//W3C//DTD XHTML 1.1//EN");
		HTMLNode htmlNode = pageNode.addChild("html", "xml:lang", L10n.getSelectedLanguage().isoCode);
			htmlNode.addChild(searchHead(plugName, search, indexuri, refresh && !js));
		
		HTMLNode bodyNode = htmlNode.addChild("body");

        // Start of body
		bodyNode.addChild(searchBox(search, indexuri, js, showold));

		bodyNode.addChild("br");
		
		bodyNode.addChild(errorDiv);

        // If showing a search
        if(request != null){
			bodyNode.addChild(progressBox(search, indexuri, request));
			bodyNode.addChild("p");

            // If search is complete show results
            if (request.getRequestStatus()==Request.RequestStatus.FINISHED)
				try{
					bodyNode.addChild(resultNodeGrouped(request, showold, js));
				}catch(Exception ex){
					addError(errorDiv, ex);
				}
			else
				bodyNode.addChild("div", "id", "results").addChild("#");
        }
		
		bodyNode.addChild("script", "type", "text/javascript").addChild("%", script(refresh,js, search, indexuri, showold));

		return pageNode.generate();
    }

	/**
	 * Return a HTMLNode for this result
	 */
	public static HTMLNode resultNode(Request request){
		// Output results
		int results = 0;

		HTMLNode node = new HTMLNode("div", "id", "results");
		HTMLNode resultTable = node.addChild("table", new String[]{"width", "class"}, new String[]{"95%", "librarian-results"});
		Iterator<URIWrapper> it = request.getResult().iterator();
		while (it.hasNext()) {
			HTMLNode entry = resultTable.addChild("tr").addChild("td").addChild("p").addChild("table", new String[]{"class", "width", "border"}, new String[]{"librarian-result", "95%", "0"});
			URIWrapper o = it.next();
			String showurl = o.URI;
			String showtitle = o.descr;
			if (showtitle.trim().length() == 0 || showtitle.equals("not available"))
				showtitle = showurl;
			String description = HTMLEncoder.encode(o.descr);
			if (!description.equals("not available")) {
				description = description.replaceAll("(\n|&lt;(b|B)(r|R)&gt;)", "<br>");
				description = description.replaceAll("  ", "&nbsp; ");
				description = description.replaceAll("&lt;/?[a-zA-Z].*/?&gt;", "");
			}
			showurl = HTMLEncoder.encode(showurl);
			if (showurl.length() > 60)
				showurl = showurl.substring(0, 15) + "&hellip;" + showurl.replaceFirst("[^/]*/", "/");
			String realurl = (o.URI.startsWith("/") ? "" : "/") + o.URI;
			String realuskurl = realurl.replaceAll("SSK@", "USK@").replaceAll("-(\\d+)/", "/$1/");
			realurl = HTMLEncoder.encode(realurl);
			entry.addChild("tr").addChild("td", new String[]{"align", "class"}, new String[]{"left", "librarian-result-title"})
				.addChild("a", new String[]{"href", "title"}, new String[]{realurl, o.URI}, showtitle);
			HTMLNode urlnode = entry.addChild("tr").addChild("td", new String[]{"align", "class"}, new String[]{"left", "librarian-result-url"});
			urlnode.addChild("a", "href", realurl, showurl);
			urlnode.addChild("#", "     ");
			if(realurl.contains("SSK@"))
					urlnode.addChild("a", new String[]{"href", "class"}, new String[]{realuskurl, "librarian-result-uskbutton"}, "[ USK ]");
			results++;
		}
		node.addChild("p").addChild("span", "class", "librarian-summary-found", xl.getString("Found")+results+xl.getString("results"));
		return node;
    }
	
	/**
	 * Return a HTMLNode for this result
	 */
	public static HTMLNode resultNodeGrouped(Request request, boolean showold, boolean js) throws Exception{
		// Output results
		int results = 0;

		HTMLNode resultsNode = new HTMLNode("div", "id", "results");
		HashMap groupmap = new HashMap();
		Iterator<URIWrapper> it = request.getResult().iterator();
		while(it.hasNext()){
			URIWrapper o = it.next();
			if(o.URI.contains("CHK"))
				groupmap.put(o.URI, o);
			else{
				if(!o.URI.contains("SSK")){
					Logger.normal(WebUI.class, "skipping " +o.URI);
					continue;
				}
				String sitebase = o.URI.replaceAll("SSK@(.+)-\\d+/.*", "$1");
				Integer sskVersion;
				try{
					sskVersion = Integer.valueOf(o.URI.replaceAll("SSK@.+-(\\d+)/.*", "$1"));
				}catch(Exception e) {
					sskVersion = Integer.valueOf(-1);
				}
				if(!groupmap.containsKey(sitebase))
					groupmap.put(sitebase, new TreeMap<Integer, Set<URIWrapper>>());
				SortedMap<Integer, Set<URIWrapper>> sitemap = (TreeMap<Integer, Set<URIWrapper>>)groupmap.get(sitebase);
				if(!sitemap.containsKey(sskVersion))
					sitemap.put(sskVersion, new HashSet());
				sitemap.get(sskVersion).add(o);
			}
		}
		Iterator<String> it2 = groupmap.keySet().iterator();
		while (it2.hasNext()) {
			String key = it2.next();
			Object ob = groupmap.get(key);
			HTMLNode siteNode = resultsNode.addChild("div");
			if(ob.getClass()==URIWrapper.class){
				URIWrapper o = (URIWrapper)ob;
				String showurl = o.URI.replaceAll("(CHK@.{5}).+(/.+)", "$1...$2");
				String showtitle = o.descr;
				if (showtitle.trim().length() == 0 || showtitle.equals("not available"))
					showtitle = showurl;
				String description = HTMLEncoder.encode(o.descr);
				if (!description.equals("not available")) {
					description = description.replaceAll("(\n|&lt;(b|B)(r|R)&gt;)", "<br>");
					description = description.replaceAll("  ", "&nbsp; ");
					description = description.replaceAll("&lt;/?[a-zA-Z].*/?&gt;", "");
				}
				String realurl = (o.URI.startsWith("/") ? "" : "/") + o.URI;
				realurl = HTMLEncoder.encode(realurl);
				siteNode.addChild("a", new String[]{"href", "title", "class"}, new String[]{realurl, o.URI, "result-title"}, showtitle);
				siteNode.addChild("br");
				siteNode.addChild("a", new String[]{"href", "class"}, new String[]{realurl, "result-url"}, showurl);
				results++;
			}else{
				Map<Integer, Set<URIWrapper>> sitemap = (Map<Integer, Set<URIWrapper>>)ob;
				Iterator<Integer> it3 = sitemap.keySet().iterator();
				HTMLNode siteBlockOldOuter = siteNode.addChild("div", new String[]{"id", "style"}, new String[]{"result-hiddenblock-"+key, (!showold?"display:none":"")});
				if(sitemap.size()>1)
					siteBlockOldOuter.addChild("a", new String[]{"onClick", "name"}, new String[]{"toggleResult('"+key+"')", key}).addChild("h3", key.replaceAll("\\b.*/(.*)", "$1"));
				HTMLNode siteBlockOld = siteBlockOldOuter.addChild("div", new String[]{"class", "style"}, new String[]{"result-hideblock", "border-left: thick black;"});
				while(it3.hasNext()){
					Integer version = it3.next();
					HTMLNode versionNode;
					boolean newestVersion = !it3.hasNext();
					if(newestVersion)	// not the newest one, hide if required
						siteBlockOld = siteNode;
					versionNode = siteBlockOld.addChild("table", new String[]{"class", "width", "border", "cellspacing", "cellpadding"}, new String[]{"librarian-result", "95%", "0px 8px", "0", "0",});
					HTMLNode grouptitle = versionNode.addChild("tr").addChild("td", new String[]{"padding", "colspan"}, new String[]{"0", "3"});
					grouptitle.addChild("h4", "style", "display:inline; padding-top: 5px; color:"+(newestVersion?"black":"darkGrey"), key.replaceAll("\\b.*/(.*)", "$1")+(version.intValue()>=0 ? "-"+version.toString():""));
					if(newestVersion && !showold && js && sitemap.size()>1)
						grouptitle.addChild("a", new String[]{"href", "onClick"}, new String[]{"#"+key, "toggleResult('"+key+"')"}, "       ["+(sitemap.size()-1)+" older matching versions]");
					HTMLNode versionrow = versionNode.addChild("tr");
					versionrow.addChild("td", "width", "8px");
					versionrow.addChild("td", new String[]{"bgcolor", "width"}, new String[]{"black", "2px"});
					HTMLNode versionCell=versionrow.addChild("td");
					Iterator<URIWrapper> it4 = sitemap.get(version).iterator();
					URIWrapper u;
					while(it4.hasNext()){
						u = it4.next();
						HTMLNode pageNode = versionCell.addChild("p", new String[]{"class", "style"}, new String[]{"result-title", "padding-left:15px"});
						String showtitle = u.descr;
						String showurl = u.URI.replaceAll("(SSK@.{5}).+(/.+)", "$1...$2");
						if (showtitle.trim().length() == 0 || showtitle.equals("not available"))
							showtitle = showurl;
						String realurl = (u.URI.startsWith("/") ? "" : "/") + u.URI;
						String realuskurl = realurl.replaceAll("SSK@", "USK@").replaceAll("-(\\d+)/", "/$1/");
						pageNode.addChild("a", new String[]{"href", "class", "style", "title"}, new String[]{realurl, "result-title", "color: "+(newestVersion?"Blue":"LightBlue"), u.URI}, showtitle);
						pageNode.addChild("a", new String[]{"href", "class"}, new String[]{realuskurl, "result-uskbutton"}, "[ USK ]");
						pageNode.addChild("br");
						pageNode.addChild("a", new String[]{"href", "class", "style"}, new String[]{realurl, "result-url", "color: "+(newestVersion?"Green":"LightGreen")}, showurl);
						results++;
					}
				}
			}
		}
		resultsNode.addChild("p").addChild("span", "class", "librarian-summary-found", xl.getString("Found")+results+xl.getString("results"));
		return resultsNode;
    }


	private static HTMLNode searchHead(String plugName, String search, String indexuri, boolean refresh){
		String title = plugName;
		if(search != null && !search.equals("") && indexuri != null && !indexuri.equals(""))
			title = "\"" + search + "\" - "+plugName;

		HTMLNode headNode = new HTMLNode("head");
		if(refresh)
            headNode.addChild("meta", new String[] { "http-equiv", "content" }, new String[] { "refresh", "1" });
		headNode.addChild("meta", new String[] { "http-equiv", "content" }, new String[] { "Content-Type", "text/html; charset=utf-8" });
		headNode.addChild("title", title);
		headNode.addChild("style").addChild("%",
				"body {font-family:sans-serif}\n" +
				".result-sitename {color:black; font-weight:bold}\n" +
				".result-table { border-spacing : 5px; }\n" +
				".result-url {color:green; font-size:small; padding-left:15px}\n" +
				".result-uskbutton {color: #480000; font-variant: small-caps; font-size: small; padding-left: 20px}\n" +
				".progress-table {border-spacing:10px 0px;}\n" +
				".progress-bar-outline { width:300px; border:1px solid grey; height : 20px;}\n" +
				".progress-bar-inner { background-color: red; height:15px; z-index:-1}\n"
				);
		return headNode;
	}

	private static HTMLNode searchBox(String search, String indexuri, boolean js, boolean showold){
		HTMLNode searchDiv = new HTMLNode("div", "id", "searchbar");
		HTMLNode searchForm = searchDiv.addChild("form", new String[]{"name", "method", "action"}, new String[]{"searchform", "GET", "plugins.XMLLibrarian.XMLLibrarian"});
			HTMLNode searchTable = searchForm.addChild("table", "width", "100%");
				HTMLNode searchTop = searchTable.addChild("tr");
					HTMLNode titleCell = searchTop.addChild("td", new String[]{"rowspan","width"},new String[]{"3","120"});
						titleCell.addChild("H1", plugName);
					HTMLNode searchcell = searchTop.addChild("td", "width", "400");
						searchcell.addChild("input", new String[]{"name", "size", "type", "value"}, new String[]{"search", "40", "text", search});
						searchcell.addChild("input", new String[]{"name", "type", "value", "tabindex"}, new String[]{"find", "submit", "Find!", "1"});
						if(js)
							searchcell.addChild("input", new String[]{"type","name"}, new String[]{"hidden","js"});

				searchTable.addChild("tr")
					.addChild("td", xl.getString("Index"))
						.addChild("input", new String[]{"name", "type", "value", "size"}, new String[]{"index", "text", indexuri, "40"});
				searchTable.addChild("tr")
					.addChild("td", xl.getString("ShowOldVersions"))
						.addChild("input", new String[]{"name", "type", showold?"checked":"size"}, new String[]{"showold", "checkbox", showold?"checked":"1"});
		return searchDiv;
	}

	private static String debugpage() {
		HTMLNode debugpage = new HTMLNode("HTML");
		HTMLNode bodynode = debugpage.addChild("body");
		for(Index i : Index.getAllIndices()){
			HTMLNode indexnode = bodynode.addChild("p");
			indexnode.addChild("#",i.toString());
		}
		return debugpage.generate();
	}

	private static HTMLNode progressBox(String search, String indexuri, Request request){
			HTMLNode progressDiv = new HTMLNode("div", "id", "progress");
            // Search description
			HTMLNode progressTable = progressDiv.addChild("table", "width", "100%");
				HTMLNode searchingforCell = progressTable.addChild("tr")
					.addChild("td");
						searchingforCell.addChild("#", xl.getString("Searching-for"));
						searchingforCell.addChild("span", "class", "librarian-searching-for-target")
							.addChild("b", search);
						searchingforCell.addChild("#", xl.getString("in-index"));
						searchingforCell.addChild("i", indexuri);


				// Search status
				HTMLNode statusRow = progressTable.addChild("tr");
					statusRow.addChild("td")
							.addChild(buildProgressNode(request));
		return progressDiv;
	}

	/**
	 * Build a node about the status of a request
	 * @return
	 */
	private static HTMLNode buildProgressNode(Request request) {
		HTMLNode node = new HTMLNode("div", "id", "librarian-search-status");
		node.addChild("table", new String[]{"id", "class"}, new String[]{"progress-table", "progress-table"}).addChild(progressBar(request));
//		if(request.getSubRequests()!=null)
//			for(Object r : request.getSubRequests())
//				node.addChild("p", " Status : "+((Request)r).getSubject()+"  "+((Request)r).getRequestStatus()+", Stage: "+((Request)r).getSubStage()+"/"+((Request)r).getSubStageCount()+", Blocks:"+((Request)r).getNumBlocksCompleted()+"/"+((Request)r).getNumBlocksTotal());
		return node;
	}
	
	private static HTMLNode progressBar(Request request) {
		HTMLNode bar; //new HTMLNode("div", new String[]{"style", "class"}, new String[]{"padding-left:20px", "progress-bar"});
		if(request.getSubRequests()==null){
			bar = new HTMLNode("tr");
			bar.addChild("td", request.getSubject());
			bar.addChild("td",
					(request.getRequestStatus()==Request.RequestStatus.INPROGRESS)
					?"Stage: "+request.getSubStage()+"/"+request.getSubStageCount()
					:request.getRequestStatus().toString());
			if(request.isFinished() || request.getNumBlocksTotal()==0){
				bar.addChild("td", ""); bar.addChild("td");
			}else{
				int percentage = (int)(100*request.getNumBlocksCompleted()/request.getNumBlocksTotal());
				bar//.addChild("#", request.getSubStage()+"/"+request.getSubStageCount())
					.addChild("td", new String[]{"class"}, new String[]{"progress-bar-outline"})
					.addChild("div", new String[]{"class", "style"}, new String[]{"progress-bar-inner", "z-index : -1; width:"+percentage+"%;"});
				bar.addChild("td", percentage+"%");
				
			}
		}else if(request.getSubject().matches(".+%.+[ ;].+")){
			bar = new HTMLNode("tbody");
			Iterator it=request.getSubRequests().iterator();
			while( it.hasNext()){
				Request r = (Request)it.next();
				HTMLNode indexrow = bar.addChild("tr");
				indexrow.addChild("td", r.getSubject().split("%")[1]);
				indexrow.addChild("td").addChild("table", "class", "progress-table").addChild(progressBar((Request)r));
			}
		}else{
			bar = new HTMLNode("#");
			Iterator it=request.getSubRequests().iterator();
			while( it.hasNext()){
				Request r = (Request)it.next();
				bar.addChild(progressBar((Request)r));
			}
		}
		return bar;
	}

	/**
	 * Put an error on the page, under node, also draws a big grey box around the error
	 */
	public static void addError(HTMLNode node, Throwable error){
		HTMLNode error1 = node.addChild("div", "style", "padding:10px;border:5px solid gray;margin:10px", error.toString());
		for (StackTraceElement ste : error.getStackTrace()){
			error1.addChild("br");
			error1.addChild("#", " -- "+ste.toString());
		}
		if(error.getCause()!=null)
			addError(error1, error.getCause());
	}

	public static String listSearches(){
		HTMLNode searchlistpage = new HTMLNode("HTML");
		HTMLNode bodynode = searchlistpage.addChild("body");
		for(String s : Search.getAllSearches().keySet()){
			HTMLNode searchnode = bodynode.addChild("p");
			searchnode.addChild("#",s);
		}
		return searchlistpage.generate();
	}

	private static String script(boolean refresh, boolean js, String searchquery, String indexuri, boolean showold){
		return  (refresh&&!js) ?
					"var loc = new String(window.location);\n" +
					"if(loc.match('\\\\?'))" +
					"	window.location=loc+'&js';\n" +
					"else\n" +
					"	window.location=loc+'?js';\n"
				:
					"\n" +
					"var url = '/plugins/plugins.XMLLibrarian.XMLLibrarian/xml?search=" +searchquery+"&index="+indexuri+"&showold="+(showold?"on":"off")+"';\n" +
					"var xmlhttp;\n" +
					"\n" +
					"function getProgress(){\n" +
					"	xmlhttp = new XMLHttpRequest();\n" +
					"	xmlhttp.onreadystatechange=xmlhttpstatechanged;\n" +
					"	xmlhttp.open('GET', url, true);\n" +
					"	xmlhttp.send(null);\n" +
					"}\n" +
					"\n" +
					"function xmlhttpstatechanged(){\n" +
					"	if(xmlhttp.readyState==4){\n" +
					"		var parser = new DOMParser();\n" +
					"		var resp = parser.parseFromString(xmlhttp.responseText, 'application/xml').documentElement;\n" +
					"		document.getElementById('librarian-search-status').innerHTML=" +
								"resp.getElementsByTagName('progress')[0].textContent;\n" +
					"		if(resp.getElementsByTagName('progress')[0].attributes.getNamedItem('requeststatus').value=='FINISHED')\n" +
					"			document.getElementById('results').innerHTML=" +
									"resp.getElementsByTagName('result')[0].textContent;\n" +
					"		else\n" +
					"			var t = setTimeout('getProgress()', 1000);\n" +
					"	}\n" +
					"}\n" +
					"getProgress();\n" +
					"\n" +
					"function toggleResult(key){\n" +
					"	var togglebox = document.getElementById('result-hiddenblock-'+key);\n" +
					"	if(togglebox.style.display == 'block')\n" +
					"		togglebox.style.display = 'none';\n" +
					"	else\n" +
					"		togglebox.style.display = 'block';\n" +
					"}\n";
	}


	static String progressxml(String searchquery, String indexuri, boolean showold) {
		HTMLNode resp = new HTMLNode("pagecontent");
		try{
			String progress;
			Search search = Search.getSearch(searchquery, indexuri);
			if(search!=null)
				progress = WebUI.buildProgressNode(search).generate();
			else
				progress = "No search for this, something went wrong";
				if(search != null && search.getRequestStatus()==Request.RequestStatus.FINISHED)
					resp.addChild("result", WebUI.resultNodeGrouped(Search.getSearch(searchquery, indexuri), showold, true).generate());
				resp.addChild("progress", "requeststatus",  (search==null)?"":search.getRequestStatus().toString(), progress);
		}catch(Exception e){
			addError(resp.addChild("progress", "requeststatus",  "ERROR"), e);
		}
		return "<?xml version='1.0' encoding='ISO-8859-1'?>\n"+resp.generate();
	}

	private static String jssafename(String in){
		return in.replaceAll("\\W+", "_");
	}
}

