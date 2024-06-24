import React from 'react';
import { Button } from '@shopify/polaris';
import func from '@/util/func';

function AktoButton({ children, url, external, download, disabled, target, submit, loading, pressed, accessibilityLabel, role, ariaControls, ariaExpanded, ariaDescribedBy, ariaChecked, onClick, onFocus, onBlur, onKeyDown, onKeyPress, onKeyUp, onMouseEnter, onTouchStart, onPointerDown, icon, primary, outline, destructive, disclosure, plain, monochrome, removeUnderline, size, textAlign, fullWidth, connectedDisclosure, dataPrimaryLink, primarySuccess }) {
    const disabledProperty = (func.isUserGuest() || disabled)

    let useProps = { disabled: disabledProperty, url, external, download, target, submit, loading, pressed, accessibilityLabel, role, ariaControls, ariaExpanded, ariaDescribedBy, ariaChecked, onClick, onFocus, onBlur, onKeyDown, onKeyPress, onKeyUp, onMouseEnter, onTouchStart, onPointerDown, icon, primary, outline, destructive, disclosure, plain, monochrome, removeUnderline, size, textAlign, fullWidth, connectedDisclosure, dataPrimaryLink, primarySuccess};

    return (
        <Button {...useProps}>{children}</Button>
    );
}

export default AktoButton;