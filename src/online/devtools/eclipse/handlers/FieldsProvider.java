package online.devtools.eclipse.handlers;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ITreeContentProvider;

class FieldsProvider implements ITreeContentProvider {

	private static final Object[] EMPTY = new Object[0];

	@Override

	public boolean hasChildren(Object element) {

		Object[] children = getElements(element);

		return children != null && children.length > 0;

	}

	@Override

	public Object getParent(Object element) {

		if (element instanceof IJavaElement) {

			return ((IJavaElement) element).getParent();

		}

		return null;

	}

	@Override

	public Object[] getElements(Object inputElement) {

		if (inputElement instanceof IType) {

			return getFields((IType) inputElement);

		}

		return EMPTY;

	}

	@Override

	public Object[] getChildren(Object parentElement) {

		return EMPTY;

	}

	public static IField[] getFields(IType type) {

		try {

			return type.getFields();

		} catch (JavaModelException e) {

			e.printStackTrace();

		}

		return new IField[0];

	}

}
