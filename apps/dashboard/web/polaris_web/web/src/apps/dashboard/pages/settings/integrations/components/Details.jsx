import AktoButton from './../../../../components/shared/AktoButton';
import { Button, HorizontalStack, LegacyCard, VerticalStack } from '@shopify/polaris'
import React from 'react'
import LineComponent from './LineComponent'
import func from '../../../../../../util/func';

function Details({onClickFunc, values}) {
    const disableButton = func.settingsAccessDenied()
    return (    
        <LegacyCard.Section title="Integration details">
            <br/>
            <VerticalStack gap={3}>
                <VerticalStack gap={2}>
                    {values.map((x,index)=> {
                        return (
                            <LineComponent title={x.title} value={x.value} key={index}/>
                        )
                    })}
                </VerticalStack>
                <HorizontalStack align="end">
                    <AktoButton disabled={disableButton} primary onClick={onClickFunc} >Delete SSO</AktoButton>
                </HorizontalStack>
            </VerticalStack>
        </LegacyCard.Section>
    )
}

export default Details