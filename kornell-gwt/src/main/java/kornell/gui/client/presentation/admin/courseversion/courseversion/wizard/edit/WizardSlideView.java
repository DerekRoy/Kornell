package kornell.gui.client.presentation.admin.courseversion.courseversion.wizard.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.github.gwtbootstrap.client.ui.Button;
import com.github.gwtbootstrap.client.ui.FileUpload;
import com.github.gwtbootstrap.client.ui.Form;
import com.github.gwtbootstrap.client.ui.Icon;
import com.github.gwtbootstrap.client.ui.TextBox;
import com.github.gwtbootstrap.client.ui.constants.AlertType;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dev.util.Name;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.place.shared.PlaceController;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import kornell.api.client.Callback;
import kornell.api.client.KornellSession;
import kornell.core.entity.ContentSpec;
import kornell.core.entity.CourseVersion;
import kornell.gui.client.presentation.admin.courseversion.courseversion.AdminCourseVersionContentView;
import kornell.gui.client.presentation.admin.courseversion.courseversion.AdminCourseVersionContentView.Presenter;
import kornell.gui.client.presentation.admin.courseversion.courseversion.autobean.wizard.Wizard;
import kornell.gui.client.presentation.admin.courseversion.courseversion.autobean.wizard.WizardElement;
import kornell.gui.client.presentation.admin.courseversion.courseversion.autobean.wizard.WizardFactory;
import kornell.gui.client.presentation.admin.courseversion.courseversion.autobean.wizard.WizardSlide;
import kornell.gui.client.presentation.admin.courseversion.courseversion.autobean.wizard.WizardSlideItem;
import kornell.gui.client.presentation.admin.courseversion.courseversion.autobean.wizard.WizardSlideItemType;
import kornell.gui.client.presentation.admin.courseversion.courseversion.autobean.wizard.WizardTopic;
import kornell.gui.client.presentation.admin.courseversion.courseversion.wizard.WizardUtils;
import kornell.gui.client.presentation.admin.courseversion.courseversion.wizard.render.WizardRenderer;
import kornell.gui.client.util.forms.FormHelper;
import kornell.gui.client.util.forms.formfield.KornellFormFieldWrapper;
import kornell.gui.client.util.view.KornellNotification;
import kornell.gui.client.util.view.LoadingPopup;

public class WizardSlideView extends Composite implements IWizardView {
	interface MyUiBinder extends UiBinder<Widget, WizardSlideView> {
	}

	private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);
	public static final WizardFactory WIZARD_FACTORY = GWT.create(WizardFactory.class);

	private EventBus bus;
	private KornellSession session;
	boolean isCurrentUser, showContactDetails, isRegisteredWithCPF;
	private FormHelper formHelper = GWT.create(FormHelper.class);
	private String PLAIN_CLASS = "plainDiscreteTextColor";
	private KornellFormFieldWrapper title;
	private List<KornellFormFieldWrapper> fields;

	@UiField	
	FlowPanel slidePanel;
	@UiField	
	FlowPanel slideViewPanel;
	@UiField
	Form form;
	@UiField
	FlowPanel slideFields;
	@UiField
	ScrollPanel slideItemsScroll;
	@UiField
	FlowPanel slidePanelItems;
	@UiField
	FlowPanel slideButtonsBar;
	@UiField
	Button btnSave;
	@UiField
	Button btnDiscard;
	@UiField
	Button btnNewTextItem;
	@UiField
	Button btnNewVideoLinkItem;
	@UiField
	Button btnNewImageItem;
	@UiField
	Button btnView;
	@UiField
	Button btnPrev;
	@UiField
	Button btnNext;
	
	private Presenter presenter;
	
	private WizardRenderer wizardRenderer;
	
	private WizardElement currentViewedWizardElement;
	
	private boolean isViewModeOn = false;
	

	private String titleLabel;
	private String changedString = "(*) ";

	private ChangeHandler refreshFormChangeHandler;
	private boolean viewModeNeedsRendering = true;

	public WizardSlideView() {
		initWidget(uiBinder.createAndBindUi(this));
		refreshFormChangeHandler = new ChangeHandler() {
			@Override
			public void onChange(ChangeEvent event) {
				refreshForm();
			}
		};
		WizardUtils.createIcon(btnPrev, "fa-arrow-left");
		WizardUtils.createIcon(btnNext, "fa-arrow-right");
		WizardUtils.createIcon(btnSave, "fa-floppy-o");
		WizardUtils.createIcon(btnDiscard, "fa-times");
		WizardUtils.createIcon(btnNewTextItem, WizardUtils.getClasForWizardSlideItemViewIcon(WizardSlideItemType.TEXT));
		WizardUtils.createIcon(btnNewVideoLinkItem, WizardUtils.getClasForWizardSlideItemViewIcon(WizardSlideItemType.VIDEO_LINK));
		WizardUtils.createIcon(btnNewImageItem, WizardUtils.getClasForWizardSlideItemViewIcon(WizardSlideItemType.IMAGE));
	} 
	
	@UiHandler("btnView")
	void doView(ClickEvent e) {
		if(!isViewModeOn && WizardUtils.wizardElementHasValueChanged(presenter.getSelectedWizardElement())){
			KornellNotification.show("Salve ou descarte suas alterações antes de visualizar.", AlertType.WARNING);
			return;
		}
		isViewModeOn = !isViewModeOn;
		if(!presenter.getSelectedWizardElement().getUUID().equals(currentViewedWizardElement.getUUID())){
			viewModeNeedsRendering = true;
			currentViewedWizardElement = presenter.getSelectedWizardElement();
		}
		toggleViewMode(isViewModeOn);
	}
	
	@UiHandler("btnPrev")
	void doPrev(ClickEvent e) {
		viewModeNeedsRendering = true;
		currentViewedWizardElement = WizardUtils.getPrevWizardElement(presenter.getWizard(), currentViewedWizardElement);
		renderViewMode(currentViewedWizardElement);
	}
	
	@UiHandler("btnNext")
	void doNext(ClickEvent e) {
		viewModeNeedsRendering = true;
		currentViewedWizardElement = WizardUtils.getNextWizardElement(presenter.getWizard(), currentViewedWizardElement);
		renderViewMode(currentViewedWizardElement);
	}
	
	@UiHandler("btnNewTextItem")
	void doNewTextItem(ClickEvent e) {
		WizardSlideItem wizardSlideItem = WizardUtils.newWizardSlideItem();
		wizardSlideItem.setWizardSlideItemType(WizardSlideItemType.TEXT);
		wizardSlideItemCreated(wizardSlideItem);
	}
	
	@UiHandler("btnNewVideoLinkItem")
	void doNewVideoLinkItem(ClickEvent e) {
		WizardSlideItem wizardSlideItem = WizardUtils.newWizardSlideItem();
		wizardSlideItem.setWizardSlideItemType(WizardSlideItemType.VIDEO_LINK);
		wizardSlideItemCreated(wizardSlideItem);
	}
	
	@UiHandler("btnNewImageItem")
	void doNewImageItem(ClickEvent e) {
		WizardSlideItem wizardSlideItem = WizardUtils.newWizardSlideItem();
		wizardSlideItem.setWizardSlideItemType(WizardSlideItemType.IMAGE);
		wizardSlideItemCreated(wizardSlideItem);
	}

	public void toggleViewMode(boolean isViewModeOn) {
		presenter.getView().getWizardView().toggleViewMode(isViewModeOn);
		
		slideViewPanel.setVisible(isViewModeOn);
		if(isViewModeOn && viewModeNeedsRendering){
			renderViewMode(currentViewedWizardElement);
		}
		if(isViewModeOn){
			slidePanel.addStyleName("fillWidth");	
		} else {
			slidePanel.removeStyleName("fillWidth");
		}
		
		slideButtonsBar.setVisible(!isViewModeOn);
		form.setVisible(!isViewModeOn);
		slideItemsScroll.setVisible(!isViewModeOn);
		
		btnView.clear();
		btnView.setTitle(isViewModeOn ? "Cancelar visualização" : "Visualizar slide");
		WizardUtils.createIcon(btnView, (isViewModeOn ? "fa-times" : "fa-eye"));

		btnPrev.setVisible(isViewModeOn);
		btnNext.setVisible(isViewModeOn);
	}

	private void renderViewMode(WizardElement wizardElement) {		
		if(wizardRenderer == null){
			wizardRenderer = new WizardRenderer(session, bus, presenter.getWizard());
			slideViewPanel.add(wizardRenderer);
		}
		wizardRenderer.render(wizardElement);

		boolean hasPrev = WizardUtils.getPrevWizardElement(presenter.getWizard(), currentViewedWizardElement) != null;
		btnPrev.setEnabled(hasPrev);

		boolean hasNext = WizardUtils.getNextWizardElement(presenter.getWizard(), currentViewedWizardElement) != null;
		btnNext.setEnabled(hasNext);
		
		viewModeNeedsRendering = false;
	}

	private void wizardSlideItemCreated(WizardSlideItem wizardSlideItem) {
		
		WizardSlide wizardSlide = (WizardSlide) presenter.getSelectedWizardElement();
		wizardSlideItem.setParentOrder(WizardUtils.buildParentOrderFromParent(wizardSlide));
		wizardSlideItem.setOrder(wizardSlide.getWizardSlideItems().size());
		wizardSlide.getWizardSlideItems().add(wizardSlideItem);
		
		WizardSlideItemView wizardSlideItemView = new WizardSlideItemView(wizardSlideItem, presenter, this);
		wizardSlideItemView.refreshForm();		
		slidePanelItems.add(wizardSlideItemView);
		
		refreshForm();
		WizardSlideItemView slideItemView;
		for(Widget widget : slidePanelItems){
			slideItemView = (WizardSlideItemView) widget;
			slideItemView.refreshForm();
		}

		slideItemsScroll.scrollToBottom();
	}

	public void setPresenter(Presenter presenter) {
		this.presenter = presenter;
	}

	public void displaySlidePanel(boolean display) {
		slidePanel.setVisible(display);
	}

	public void updateSlidePanel() {
		WizardElement selectedWizardElement = presenter.getSelectedWizardElement();
		this.fields = new ArrayList<KornellFormFieldWrapper>();
		slideFields.clear();	
		slidePanelItems.clear();
		slideItemsScroll.scrollToTop();

		titleLabel = "Título do Slide";
		title = new KornellFormFieldWrapper(titleLabel, formHelper.createTextBoxFormField(selectedWizardElement.getTitle()), true);
		((TextBox)title.getFieldWidget()).addChangeHandler(refreshFormChangeHandler);
		fields.add(title);
		slideFields.add(title);		

		if(selectedWizardElement instanceof WizardSlide){
			WizardSlide wizardSlide = (WizardSlide) selectedWizardElement;
			WizardSlideItemView wizardSlideItemView; 
			for (final WizardSlideItem wizardSlideItem : wizardSlide.getWizardSlideItems()) {
				wizardSlideItemView = new WizardSlideItemView(wizardSlideItem, presenter, this);
				slidePanelItems.add(wizardSlideItemView);
			} 
		}
		currentViewedWizardElement = selectedWizardElement;
		//viewModeNeedsRendering = true;
		//isViewModeOn = true;
		//toggleViewMode(true);
		toggleViewMode(false);
	}
	
	public void reorderItems(){
		List<WizardSlideItemView> itemList = new ArrayList<>(); 
		WizardSlideItemView itemView;
		boolean reorderNeeded = false;
		for(int i = 0; i < slidePanelItems.getWidgetCount(); i++){
			itemView = (WizardSlideItemView)slidePanelItems.getWidget(i);
			itemList.add(itemView);
			if(i != itemView.getDisplayOrder()){
				reorderNeeded = true;
			}
		}
		
		if(reorderNeeded){
			slidePanelItems.clear();
			Collections.sort(itemList, WizardUtils.COMPARE_WIZARD_SLIDE_ITEM_VIEWS_BY_DISPLAY_ORDER);
			for(int i = 0; i < itemList.size(); i++){
				slidePanelItems.add(itemList.get(i));
			}
		}
		
		refreshForm();
	}

	@Override
	public void resetFormToOriginalValues(){
		WizardElement selectedWizardElement = presenter.getSelectedWizardElement();
		
		((TextBox)title.getFieldWidget()).setText(selectedWizardElement.getTitle());
		WizardSlideItemView wizardSlideItemView;
		Widget widget;
		for(int i = 0; i < slidePanelItems.getWidgetCount(); i++){
			widget = slidePanelItems.getWidget(i);
			wizardSlideItemView = (WizardSlideItemView) widget;
			
			if(wizardSlideItemView.getWizardSlideItem().getUUID().startsWith("new")){
				slidePanelItems.remove(widget);
				i--;
				//remove from model
				for(WizardSlideItem wizardSlideItem : ((WizardSlide)selectedWizardElement).getWizardSlideItems()){
					if(wizardSlideItem.equals(wizardSlideItemView.getWizardSlideItem())){
						((WizardSlide)selectedWizardElement).getWizardSlideItems().remove(wizardSlideItem);
						break;
					}
				}
				continue;
			}
			
			wizardSlideItemView.resetFormToOriginalValues();
		}
		reorderItems();

		resetOrders();

		viewModeNeedsRendering = true;
		presenter.valueChanged(selectedWizardElement, false);
		refreshForm();
	}
	
	public void resetOrders(){
		WizardSlideItemView wizardSlideItemView;
		int order = 0;
		for(int i = 0; i < slidePanelItems.getWidgetCount(); i++){
			wizardSlideItemView = (WizardSlideItemView) slidePanelItems.getWidget(i);
			
			wizardSlideItemView.setDisplayOrder(order);
			((WizardSlideItemView)wizardSlideItemView).getWizardSlideItem().setOrder(order);
			order++;
			
			wizardSlideItemView.resetFormToOriginalValues();
		}
	}
	
	@Override
	public boolean refreshForm(){
		WizardElement selectedWizardElement = presenter.getSelectedWizardElement();
		
		boolean valueHasChanged = updateTitleFormElement(selectedWizardElement.getTitle());
		
		presenter.valueChanged(valueHasChanged);
		
		validateFields();
		
		return valueHasChanged;
	}
	 
	private boolean updateTitleFormElement(String originalValue){
		boolean valueHasChanged = !title.getFieldPersistText().equals(originalValue);
		presenter.valueChanged(valueHasChanged);
		title.setFieldLabelText((valueHasChanged ? changedString  : "") + titleLabel);
		return valueHasChanged;
	}
	
	@UiHandler("btnSave")
	void doOK(ClickEvent e) {
		if(!WizardUtils.wizardElementHasValueChanged(presenter.getSelectedWizardElement())){
			KornellNotification.show("Alterações salvas com sucesso.");
			return;
		}
		updateWizard();
	}
	
	@UiHandler("btnDiscard")
	void doDiscard(ClickEvent e) {
		if(!WizardUtils.wizardElementHasValueChanged(presenter.getSelectedWizardElement())){
			return;
		}
		
		resetFormToOriginalValues();
	}

	@Override
	public boolean validateFields() {	
		formHelper.clearErrors(fields);
		
		boolean errorsFound = false;
		
		if (!formHelper.isLengthValid(title.getFieldPersistText(), 2, 100)) {
			title.setError("Insira o título");
		}
		
		for(Widget wizardSlideItemView : slidePanelItems){
			errorsFound = errorsFound || !((WizardSlideItemView)wizardSlideItemView).validateFields();
		}
		
		errorsFound = errorsFound || formHelper.checkErrors(fields);
		return !errorsFound;
	}

	@Override
	public void updateWizard() {

		if (validateFields()) {
			WizardElement selectedWizardElement = presenter.getSelectedWizardElement();
			selectedWizardElement.setTitle(title.getFieldPersistText());
			
			for(Widget wizardSlideItemView : slidePanelItems){
				((WizardSlideItemView)wizardSlideItemView).updateWizard();
			}

			//@TODO CALLBACK
			if(selectedWizardElement instanceof WizardSlide){
				Collections.sort(((WizardSlide)selectedWizardElement).getWizardSlideItems(), WizardUtils.COMPARE_WIZARD_ELEMENT_BY_ORDER);
			}
			presenter.valueChanged(false);
			
			WizardSlideItemView wizardSlideItemView;
			for(Widget widget : slidePanelItems){
				wizardSlideItemView = (WizardSlideItemView) widget;
				wizardSlideItemView.getWizardSlideItem().setValueChanged(false);
				if(wizardSlideItemView.getWizardSlideItem().getUUID().startsWith("new")){
					wizardSlideItemView.getWizardSlideItem().setUUID("" + Math.random());
				}
				
				wizardSlideItemView.refreshForm();
			}

			resetOrders();
			
			viewModeNeedsRendering = true;
			KornellNotification.show("Alterações salvas com sucesso.");
			reorderItems();
			//@TODO CALLBACK

		} else {
			KornellNotification.show("Existem erros nos dados.", AlertType.ERROR);
		}
	}

	public void moveDownItem(WizardSlideItem wizardSlideItem) {
		WizardSlideItemView wizardSlideItemView;
		WizardSlideItemView targetWizardSlideItemView = null;
		for(Widget widget : slidePanelItems){
			wizardSlideItemView = (WizardSlideItemView) widget;
			if(targetWizardSlideItemView != null){
				int tmpDisplayOrder = targetWizardSlideItemView.getDisplayOrder();
				targetWizardSlideItemView.setDisplayOrder(wizardSlideItemView.getDisplayOrder());
				wizardSlideItemView.setDisplayOrder(tmpDisplayOrder);
				reorderItems();
				return;
			}
			if(wizardSlideItem.getUUID().equals(wizardSlideItemView.getWizardSlideItem().getUUID())){
				targetWizardSlideItemView = (WizardSlideItemView) widget;
			}
		}
	}

	public void moveUpItem(WizardSlideItem wizardSlideItem) {
		WizardSlideItemView wizardSlideItemView;
		WizardSlideItemView previousWizardSlideItemView = null;
		for(Widget widget : slidePanelItems){
			wizardSlideItemView = (WizardSlideItemView) widget;
			if(wizardSlideItem.getUUID().equals(wizardSlideItemView.getWizardSlideItem().getUUID())){
				int tmpDisplayOrder = previousWizardSlideItemView.getDisplayOrder();
				previousWizardSlideItemView.setDisplayOrder(wizardSlideItemView.getDisplayOrder());
				wizardSlideItemView.setDisplayOrder(tmpDisplayOrder);
				reorderItems();
				wizardSlideItemView.getElement().scrollIntoView();
			}
			previousWizardSlideItemView = wizardSlideItemView;
		}
	}
	
	public int getWizardSlideItemViewCount(){
		return slidePanelItems.getWidgetCount();
	}

	public void deleteItem(WizardSlideItem wizardSlideItem) {
		WizardSlide wizardSlide = (WizardSlide) presenter.getSelectedWizardElement();
		wizardSlide.getWizardSlideItems().remove(wizardSlideItem);

		WizardSlideItemView wizardSlideItemView;
		WizardSlideItemView targetWizardSlideItemView = null;
		for(Widget widget : slidePanelItems){
			wizardSlideItemView = (WizardSlideItemView) widget;
			if(targetWizardSlideItemView != null){
				//make sure that only non-moved items have their orders changed
				if(wizardSlideItemView.getWizardSlideItem().getOrder().equals(wizardSlideItemView.getDisplayOrder())){
					wizardSlideItemView.getWizardSlideItem().setOrder(wizardSlideItemView.getWizardSlideItem().getOrder() - 1);
				}
				wizardSlideItemView.setDisplayOrder(wizardSlideItemView.getDisplayOrder() - 1);
			}
			if(wizardSlideItem.getUUID().equals(wizardSlideItemView.getWizardSlideItem().getUUID())){
				targetWizardSlideItemView = (WizardSlideItemView) widget;
			}
			wizardSlideItemView.refreshForm();
		}
		slidePanelItems.remove(targetWizardSlideItemView);
		if(!wizardSlideItem.getUUID().startsWith("new")){
			//@TODO CALLBACK
			
			//@TODO CALLBACK
		}
		refreshForm();
	}
}