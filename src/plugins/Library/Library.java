/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library;

import plugins.Library.library.Index;
import plugins.Library.library.WriteableIndex;
import plugins.Library.index.xml.XMLIndex;
import plugins.Library.index.ProtoIndex;
import plugins.Library.index.ProtoIndexSerialiser;
import plugins.Library.search.InvalidSearchException;
import plugins.Library.client.FreenetArchiver;
import plugins.Library.io.ObjectStreamReader;
import plugins.Library.io.ObjectStreamWriter;

import plugins.KeyExplorer.KeyExplorerUtils;
import plugins.KeyExplorer.GetResult;

import freenet.client.ArchiveManager.ARCHIVE_TYPE;
import freenet.client.FetchException;
import freenet.client.Metadata;
import freenet.client.MetadataParseException;
import freenet.client.async.KeyListenerConstructionException;
import freenet.keys.FreenetURI;
import freenet.node.LowLevelGetException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.io.BucketTools;
import freenet.support.Logger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.security.MessageDigest;


/**
 * Library class is the api for others to use search facilities, it is used by the interfaces
 * @author MikeB
 */
public class Library {

	public static final String BOOKMARK_PREFIX = "bookmark:";
	public static final String DEFAULT_INDEX_SITE = BOOKMARK_PREFIX + "freenetindex";
	private static int version = 1;
	public static final String plugName = "Library " + getVersion();

	public static String getPlugName() {
		return plugName;
	}

	public static long getVersion() {
		return version;
	}

	/**
	** Library singleton.
	*/
	private static Library lib;

	public synchronized static Library init(PluginRespirator pr) {
		if (lib != null) {
			throw new IllegalStateException("Library already initialised");
		}
		return lib = new Library(pr);
	}

	private PluginRespirator pr;

	/**
	 * Method to setup Library class so it has access to PluginRespirator, and load bookmarks
	 * TODO pull bookmarks from disk
	 */
	private Library(PluginRespirator pr){
		this.pr = pr;
		try {
			addBookmark("wanna", "USK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search/19/");
			addBookmark("freenetindex", "USK@US6gHsNApDvyShI~sBHGEOplJ3pwZUDhLqTAas6rO4c,3jeU5OwV0-K4B6HRBznDYGvpu2PRUuwL0V110rn-~8g,AQACAAE/freenet-index/2/");
		} catch (MalformedURLException ex) {
			Logger.error(this, "Error putting bookmarks", ex);
		}
	}

	/**
	** Holds all the read-indexes.
	*/
	private Map<String, Index> rtab = new HashMap<String, Index>();

	/**
	** Holds all the writeable indexes.
	*/
	private Map<String, WriteableIndex> wtab = new HashMap<String, WriteableIndex>();

	/**
	** Holds all the bookmarks (aliases into the rtab).
	*/
	private Map<String, String> bookmarks = new HashMap<String, String>();

	// PRIORITY make this throw a checked exception
	public Class<?> getIndexType(String indexuri) {
		List<String> errors = new ArrayList<String>();
		try {
			FreenetURI uri = KeyExplorerUtils.sanitizeURI(errors, indexuri);
			GetResult getresult  = KeyExplorerUtils.simpleGet(pr, uri);
			byte[] data = BucketTools.toByteArray(getresult.getData());

			if (getresult.isMetaData()) {
				try {
					Metadata md = Metadata.construct(data);

					if (md.isArchiveManifest()) {
						if (md.getArchiveType() == ARCHIVE_TYPE.TAR) {
							return getIndexTypeFromManifest(uri, false, true);

						} else if (md.getArchiveType() == ARCHIVE_TYPE.ZIP) {
							return getIndexTypeFromManifest(uri, true, false);

						} else {
							throw new UnsupportedOperationException("not implemented - unknown archive manifest");
						}

					} else if (md.isSimpleManifest()) {
						return getIndexTypeFromManifest(uri, false, false);
					}

					return getIndexTypeFromSimpleMetadata(md);

				} catch (MetadataParseException e) {
					throw new RuntimeException(e);
				}
			} else {
				throw new UnsupportedOperationException("Found data instead of metadata; I do not have enough intelligence to decode this.");
			}

		} catch (Exception e) {
			//java.net.MalformedURLException
			//freenet.node.LowLevelGetException
			//java.io.IOException
			// PRIORITY make this throw a checked exception
			throw new RuntimeException(e);
		}
	}

	public Class<?> getIndexTypeFromSimpleMetadata(Metadata md) {
		String mime = md.getMIMEType();
		if (mime.equals(ProtoIndex.MIME_TYPE)) {
			//return "YAML index";
			return ProtoIndex.class;
		} else if (mime.equals(XMLIndex.MIME_TYPE)) {
			//return "XML index";
			return XMLIndex.class;
		} else {
			throw new UnsupportedOperationException("Unknown mime-type for index");
		}
	}

	public Class<?> getIndexTypeFromManifest(FreenetURI furi, boolean zip, boolean tar)
		throws MalformedURLException, FetchException, IOException, MetadataParseException, LowLevelGetException, KeyListenerConstructionException {

		boolean automf = true, deep = true, ml = true;
		Metadata md = null;

		if (zip)
			md = KeyExplorerUtils.zipManifestGet(pr, furi);
		else if (tar)
			md = KeyExplorerUtils.tarManifestGet(pr, furi, ".metadata");
		else {
			md = KeyExplorerUtils.simpleManifestGet(pr, furi);
			if (ml) {
				md = KeyExplorerUtils.splitManifestGet(pr, md);
			}
		}

		if (md.isSimpleManifest()) {
			// a subdir
			HashMap<String, Metadata> docs = md.getDocuments();
			Metadata defaultDoc = md.getDefaultDocument();

			if (defaultDoc != null) {
				//return "(default doc method) " + getIndexTypeFromSimpleMetadata(defaultDoc);
				return getIndexTypeFromSimpleMetadata(defaultDoc);
			}

			if (docs.containsKey(ProtoIndex.DEFAULT_FILE)) {
				//return "(doclist method) YAML index";
				return ProtoIndex.class;
			} else if (docs.containsKey(XMLIndex.DEFAULT_FILE)) {
				//return "(doclist method) XML index";
				return XMLIndex.class;
			} else {
				throw new UnsupportedOperationException("Could not find a supported index in the document-listings for " + furi.toString());
			}
		}

		throw new UnsupportedOperationException("Parsed metadata but did not reach a simple manifest: " + furi.toString());
	}

	/**
	 * Add a new bookmark,
	 * TODO there should be a separate version for untrusted adds from freesites which throws some Security Exception
	 * @param name of new bookmark
	 * @param uri of new bookmark, must be FrenetURI
	 * @return reference of new bookmark
	 * @throws java.net.MalformedURLException if uri is not a FreenetURI
	 */
	public String addBookmark(String name, String uri) throws MalformedURLException {
		bookmarks.put(name, ( new FreenetURI(uri) ).toString());
		return name;
	}
	public Set<String> bookmarkKeys() {
		return bookmarks.keySet();
	}

	/**
	 * Returns a set of Index objects one for each of the uri's specified
	 * gets an existing one if its there else makes a new one
	 *
	 * @param indexuris list of index specifiers separated by spaces
	 * @return Set of Index objects
	 */
	public final ArrayList<Index> getIndices(String indexuris) throws InvalidSearchException{
		String[] uris = indexuris.split("[ ;]");
		ArrayList<Index> indices = new ArrayList<Index>(uris.length);

		for ( String uri : uris){
			indices.add(getIndex(uri));
		}
		return indices;
	}

	/**
	 * Method to get all of the instatiated Indexes
	 */
	public final Iterable<Index> getAllIndices() {
		return rtab.values();
	}

	/**
	 * Gets an index using its id in the form {type}:{uri} <br />
	 * known types are xml, bookmark
	 * @param indexid
	 * @return Index object
	 * @throws plugins.Library.util.InvalidSearchException
	 */
	/**
	 * Returns an Index object for the uri specified,
	 * gets an existing one if its there else makes a new one
	 *
	 * TODO : identify all index types so index doesn't need to refer to them directly
	 * @param indexuri index specifier
	 * @return Index object
	 */
	// PRIORITY make this throw a checked exception for when getIndexType fails, or
	// to wrap around UnsupportedOperationException
	public final Index getIndex(String indexuri) throws InvalidSearchException, UnsupportedOperationException {
		Logger.normal(this, "Getting index "+indexuri);
		indexuri = indexuri.trim();
		if (indexuri.startsWith(BOOKMARK_PREFIX)){
			indexuri = indexuri.substring(BOOKMARK_PREFIX.length());
			if(indexuri.matches(".+\\(.+\\)"))
				try {
					indexuri = addBookmark(indexuri.split("[()]")[0], indexuri.split("[()]")[1]);
				} catch (MalformedURLException ex) {
					throw new InvalidSearchException("Bookmark target is not a valid Freenet URI", ex);
				}
			if (bookmarks.containsKey(indexuri))
				return getIndex(bookmarks.get(indexuri));
			else
				throw new InvalidSearchException("Index bookmark '"+indexuri+" does not exist");
		}

		if (!indexuri.endsWith("/"))
			indexuri += "/";
		if (rtab.containsKey(indexuri))
			return rtab.get(indexuri);

		Index index;

		// PRIORITY maybe make this non-blocking. though getting the top block(s) shouldn't take long?
		Class<?> indextype = getIndexType(indexuri);
		if (indextype == ProtoIndex.class) {
			throw new UnsupportedOperationException("YAML index not implemented yet");

		} else if (indextype == XMLIndex.class) {
			index = new XMLIndex(indexuri, pr);

		} else {
			throw new UnsupportedOperationException("Unrecognised index type.");
		}

		rtab.put(indexuri, index);
		Logger.normal(this, "Loaded index type " + indextype.getName() + " at " + indexuri);

		return index;
	}


	/**
	** Create a {@link FreenetArchiver} connected to the core of the
	** singleton's {@link PluginRespirator}.
	**
	** @throws IllegalStateException if the singleton has not been initialised
	**         or if it does not have a respirator.
	*/
	public static <T extends Map<String, ? extends Object>> FreenetArchiver<T>
	makeArchiver(ObjectStreamReader r, ObjectStreamWriter w, FreenetURI uskbase, String mime, int size) {
		if (lib == null || lib.pr == null) {
			throw new IllegalStateException("Cannot archive to freenet without a fully live Library plugin connected to a freenet node.");
		} else {
			return new FreenetArchiver<T>(lib.pr.getNode().clientCore, r, w, uskbase, mime, size);
		}
	}

	/**
	** Create a {@link FreenetArchiver} connected to the core of the
	** singleton's {@link PluginRespirator}.
	**
	** @throws IllegalStateException if the singleton has not been initialised
	**         or if it does not have a respirator.
	*/
	public static <T extends Map<String, ? extends Object>, S extends ObjectStreamWriter & ObjectStreamReader> FreenetArchiver<T>
	makeArchiver(S rw, FreenetURI uskbase, String mime, int size) {
		return makeArchiver(rw, rw, uskbase, mime, size);
	}

	public static String convertToHex(byte[] data) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < data.length; i++) {
			int halfbyte = (data[i] >>> 4) & 0x0F;
			int two_halfs = 0;
			do {
				if ((0 <= halfbyte) && (halfbyte <= 9))
					buf.append((char) ('0' + halfbyte));
				else
					buf.append((char) ('a' + (halfbyte - 10)));
				halfbyte = data[i] & 0x0F;
			} while (two_halfs++ < 1);
		}
		return buf.toString();
	}

	//this function will return the String representation of the MD5 hash for the input string
	public static String MD5(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] b = text.getBytes("UTF-8");
			md.update(b, 0, b.length);
			byte[] md5hash = md.digest();
			return convertToHex(md5hash);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
