/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index;

import plugins.Library.util.Skeleton;
import plugins.Library.util.SkeletonTreeMap;
import plugins.Library.util.SkeletonBTreeMap;
import plugins.Library.util.SkeletonBTreeSet;
import plugins.Library.util.DataNotLoadedException;
import plugins.Library.serial.Serialiser;
import plugins.Library.serial.TaskAbortException;
import plugins.Library.serial.Progress;
import plugins.Library.serial.ProgressTracker;

import freenet.keys.FreenetURI;

import java.util.Collections;
import java.util.Iterator;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.SortedSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.Stack;
import java.util.Date;

/**
** Prototype B-tree based index. DOCUMENT
**
** @author infinity0
*/
public class ProtoIndex {

	final static long serialVersionUID = 0xf82a9082681e5ba6L;

	// DEBUG make final again later
	/*final*/ public static int BTREE_NODE_MIN = 0x10000;
	final public static int BTREE_ENT_MAX = (BTREE_NODE_MIN<<1) - 1;

	/**
	** Freenet ID for this index
	*/
	protected FreenetURI id;

	/**
	** Name for this index.
	*/
	protected String name;

	/**
	** Last time this index was modified.
	*/
	protected Date modified;

	/**
	** Extra configuration options for the index.
	*/
	final protected Map<String, Object> extra;



	final protected SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> ttab;
	final protected SkeletonBTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>> utab;


	public ProtoIndex(FreenetURI i, String n) {
		id = i;
		name = (n == null)? "": n;
		modified = new Date();
		extra = new HashMap<String, Object>();

		utab = new SkeletonBTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>(BTREE_NODE_MIN);
		ttab = new SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>>(BTREE_NODE_MIN);
		//filtab = new SkeletonPrefixTreeMap<Token, TokenFilter>(new Token(), TKTAB_MAX);
	}

	protected ProtoIndex(FreenetURI i, String n, Date m, Map<String, Object> x,
		SkeletonBTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>> u,
		SkeletonBTreeMap<String, SkeletonBTreeSet<TermEntry>> t/*,
		SkeletonMap<Token, TokenFilter> f*/
		) {
		id = i;
		name = (n == null)? "": n;
		modified = m;
		extra = x;

		//filtab = f;
		ttab = t;
		utab = u;
	}



	private Map<String, Request<Collection<TermEntry>>> getTermEntriesProgress = new
	HashMap<String, Request<Collection<TermEntry>>>();

	public Request<Collection<TermEntry>> getTermEntries(String term) {
		Request<Collection<TermEntry>> request = getTermEntriesProgress.get(term);
		if (request == null) {
			request = new getTermEntriesHandler(term);
			getTermEntriesProgress.put(term, request);
			(new Thread((Runnable)request)).start();
		}
		return request;
	}




	public Request<URIEntry> getURIEntry(FreenetURI uri) {
		throw new UnsupportedOperationException("not implemented");
	}




	public class getTermEntriesHandler extends AbstractRequest<Collection<TermEntry>> implements Runnable {

		final Stack<Object> objects = new Stack<Object>();
		final Stack<ProgressTracker> trackers = new Stack<ProgressTracker>();

		protected getTermEntriesHandler(String t) {
			super(t);
		}

		@Override public int partsDone() {
			throw new UnsupportedOperationException("not implemented");
		}

		@Override public int partsTotal() {
			throw new UnsupportedOperationException("not implemented");
		}

		@Override public int finalTotalEstimate() {
			throw new UnsupportedOperationException("not implemented");
		}

		@Override public boolean isTotalFinal() {
			throw new UnsupportedOperationException("not implemented");
		}

		protected Collection<TermEntry> resultreturn;
		/**
		** {@inheritDoc}
		**
		** This implementation returns an immutable collection backed by the
		** data stored in the Library.
		*/
		@Override public Collection<TermEntry> getResult() throws TaskAbortException {
			if (error != null) { throw error; }
			if (result != null && resultreturn == null) {
				resultreturn = Collections.unmodifiableCollection(result);
			}
			return resultreturn;
		}

		@Override public String getCurrentStatus() {
			if (objects.size() == 0) { return "nothing yet"; }
			Progress p = trackers.peek().getPullProgress(objects.peek());
			return (p == null)? "waiting for next stage to start": p.getStatus();
		}

		@Override public String getCurrentStage() {
			if (objects.size() == 0) { return "nothing yet"; }
			Progress p = trackers.peek().getPullProgress(objects.peek());
			return (p == null)? "waiting for next stage to start": p.getName();
		}

		@Override public void run() {
			// get the root container
			SkeletonBTreeSet<TermEntry> root;
			for (;;) {
				try {
					root = ttab.get(subject);
					break;
				} catch (DataNotLoadedException d) {
					Skeleton p = d.getParent();
					objects.push(d.getValue());
					trackers.push(((Serialiser.Trackable)p.getSerialiser()).getTracker());
					try {
						p.inflate(d.getKey());
					} catch (TaskAbortException e) {
						error = e;
						return;
					}
				}
			}
			// get the container contents
			Collection<TermEntry> tmp = new TreeSet<TermEntry>();
			for (Iterator<TermEntry> it = root.iterator(); it.hasNext();) {
				// OPTIMISE atm this iterations through the entries and inflates
				// them in turn; need to parallelise this
				// (the below code will be extended to allow retrieving a *subset*
				// of the all the entries, not just inflate() everything)
				try {
					tmp.add(it.next());
				} catch (DataNotLoadedException d) {
					Skeleton p = d.getParent();
					objects.push(d.getValue());
					trackers.push(((Serialiser.Trackable)p.getSerialiser()).getTracker());
					try {
						p.inflate(d.getKey());
					} catch (TaskAbortException e) {
						error = e;
						return;
					}
				}
			}
			result = tmp;
		}

	}



}
