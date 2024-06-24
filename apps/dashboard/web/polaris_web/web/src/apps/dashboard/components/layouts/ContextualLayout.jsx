import React from 'react'
import { Frame, ContextualSaveBar } from '@shopify/polaris'
import "./ContextualLayoutStyle.css"
import PersistStore from '../../../main/PersistStore'
import func from '@/util/func'

function ContextualLayout(props){
    const disableButton = func.inventoryAccessDenied()

    const {saveAction, discardAction, isDisabled, pageMarkup } = props

    const logo = {
        width: 124,
        contextualSaveBarSource:'/public/logo.svg',
        url: '#',
        accessibilityLabel: 'Akto Icon',
      };
    
    const contextualMarkup = (
    <ContextualSaveBar
            message="Unsaved changes"
            saveAction={{
            onAction: () => saveAction(),
            loading: false,
            disabled: (disableButton || isDisabled()),
            content: "Save"
            }}
            discardAction={{
            onAction: () => discardAction(),
            content: "Discard",
            disabled: (disableButton || isDisabled()),
            }}
        />
    )

    return (
        <div className='control-frame-padding'>
          <Frame logo={logo}>
            {contextualMarkup}
            {pageMarkup}
          </Frame>
        </div>
      )
}

export default ContextualLayout