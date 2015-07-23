/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Arbalo AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.opentdc.gifts.file;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.file.AbstractFileServiceProvider;
import org.opentdc.gifts.GiftModel;
import org.opentdc.gifts.ServiceProvider;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.util.PrettyPrinter;

/**
 * A file-based or transient implementation of the Gifts service.
 * @author Bruno Kaiser
 *
 */
public class FileServiceProvider extends AbstractFileServiceProvider<GiftModel> implements ServiceProvider {
	
	private static Map<String, GiftModel> index = null;
	private static final Logger logger = Logger.getLogger(FileServiceProvider.class.getName());

	/**
	 * Constructor.
	 * @param context the servlet context.
	 * @param prefix the simple class name of the service provider
	 * @throws IOException
	 */
	public FileServiceProvider(
		ServletContext context, 
		String prefix
	) throws IOException {
		super(context, prefix);
		if (index == null) {
			index = new HashMap<String, GiftModel>();
			List<GiftModel> _gifts = importJson();
			for (GiftModel _gift : _gifts) {
				index.put(_gift.getId(), _gift);
			}
			logger.info(_gifts.size() + " Gifts imported.");
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.gifts.ServiceProvider#list(java.lang.String, java.lang.String, long, long)
	 */
	@Override
	public ArrayList<GiftModel> list(
		String queryType,
		String query,
		int position,
		int size
	) {
		ArrayList<GiftModel> _gifts = new ArrayList<GiftModel>(index.values());
		Collections.sort(_gifts, GiftModel.GiftComparator);
		ArrayList<GiftModel> _selection = new ArrayList<GiftModel>();
		for (int i = 0; i < _gifts.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_gifts.get(i));
			}			
		}
		logger.info("list(<" + query + ">, <" + queryType + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size() + " gifts.");
		return _selection;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.gifts.ServiceProvider#create(org.opentdc.gifts.GiftModel)
	 */
	@Override
	public GiftModel create(
		GiftModel gift) 
	throws DuplicateException, ValidationException {
		logger.info("create(" + PrettyPrinter.prettyPrintAsJSON(gift) + ")");
		String _id = gift.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (index.get(_id) != null) {
				// object with same ID exists already
				throw new DuplicateException("gift <" + _id + "> exists already.");
			}
			else { 	// a new ID was set on the client; we do not allow this
				throw new ValidationException("gift <" + _id + 
					"> contains an ID generated on the client. This is not allowed.");
			}
		}
		// enforce mandatory fields
		if (gift.getTitle() == null || gift.getTitle().length() == 0) {
			throw new ValidationException("gift <" + _id + 
					"> must contain a valid title.");
		}

		gift.setId(_id);
		Date _date = new Date();
		gift.setCreatedAt(_date);
		gift.setCreatedBy(getPrincipal());
		gift.setModifiedAt(_date);
		gift.setModifiedBy(getPrincipal());
		index.put(_id, gift);
		logger.info("create(" + PrettyPrinter.prettyPrintAsJSON(gift) + ")");
		if (isPersistent) {
			exportJson(index.values());
		}
		return gift;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.gifts.ServiceProvider#read(java.lang.String)
	 */
	@Override
	public GiftModel read(
		String id) 
	throws NotFoundException {
		GiftModel _gift = index.get(id);
		if (_gift == null) {
			throw new NotFoundException("no gift with ID <" + id
					+ "> was found.");
		}
		logger.info("read(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_gift));
		return _gift;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.gifts.ServiceProvider#update(java.lang.String, org.opentdc.gifts.GiftModel)
	 */
	@Override
	public GiftModel update(
		String id, 
		GiftModel gift
	) throws NotFoundException, ValidationException {
		GiftModel _gift = index.get(id);
		if(_gift == null) {
			throw new NotFoundException("no gift with ID <" + id
					+ "> was found.");
		} 
		if (! _gift.getCreatedAt().equals(gift.getCreatedAt())) {
			logger.warning("gift <" + id + ">: ignoring createdAt value <" + gift.getCreatedAt().toString() + 
					"> because it was set on the client.");
		}
		if (! _gift.getCreatedBy().equalsIgnoreCase(gift.getCreatedBy())) {
			logger.warning("gift <" + id + ">: ignoring createdBy value <" + gift.getCreatedBy() +
					"> because it was set on the client.");
		}
		_gift.setTitle(gift.getTitle());
		_gift.setDescription(gift.getDescription());
		_gift.setModifiedAt(new Date());
		_gift.setModifiedBy(getPrincipal());
		index.put(id, _gift);
		logger.info("update(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_gift));
		if (isPersistent) {
			exportJson(index.values());
		}
		return _gift;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.gifts.ServiceProvider#delete(java.lang.String)
	 */
	@Override
	public void delete(
		String id) 
	throws NotFoundException, InternalServerErrorException {
		GiftModel _gift = index.get(id);
		if (_gift == null) {
			throw new NotFoundException("gift <" + id
					+ "> was not found.");
		}
		if (index.remove(id) == null) {
			throw new InternalServerErrorException("gift <" + id
					+ "> can not be removed, because it does not exist in the index");
		}
		logger.info("delete(" + id + ")");
		if (isPersistent) {
			exportJson(index.values());
		}
	}
}
