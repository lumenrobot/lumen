package org.lskk.lumen.persistence.web;

import org.apache.wicket.Page;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import javax.annotation.Nullable;

/**
 * {@link LinkPanel} / {@link LinkColumn} can be used by anything (e.g. name), not just ID.
 *
 * @param <T>
 * @param <S>
 * @author rudi
 * @see LinkColumn
 */
public class LinkPanel<T, S> extends Panel {

	public LinkPanel(final String componentId,
					 final Class<? extends Page> pageClass, final PageParameters params,
					 final IModel<String> labelModel) {
		this(componentId, pageClass, params, labelModel, null);
	}
	
	public LinkPanel(final String componentId,
					 final Class<? extends Page> pageClass, final PageParameters params,
					 final IModel<String> labelModel, @Nullable TagType tagType) {
		super(componentId);
		final BookmarkablePageLink<Page> link = new BookmarkablePageLink<>("link", pageClass, params);
		if (tagType != null) {
			link.add(new TagLabel("label", labelModel, tagType));
		} else {
			link.add(new Label("label", labelModel));
		}
		add(link);
		add(new WebMarkupContainer("lock") {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(!isEnabledInHierarchy());
			}
		});
	}
	
}