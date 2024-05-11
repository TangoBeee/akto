import { Button, HorizontalStack, Text, VerticalStack, Box, Spinner, Divider, Scrollable } from "@shopify/polaris"
import {
    CancelMajor
} from '@shopify/polaris-icons';
import "./style.css"

function FlyLayout(props) {

    const { title, titleComp,  show, setShow, components,loading, showDivider, newComp, handleClose, isHandleClose, width} = props
    const handleExit = () => {
        setShow(!show)
        if(isHandleClose){
            handleClose()
        }
    }
    const divWidth = width || "50vw";
    return (
        <div className={"flyLayout " + (show ? "show" : "")} style={{width: divWidth}}>
            <div className="innerFlyLayout">
                <Box borderColor="border-subdued" borderWidth="1" background="bg" width={divWidth} minHeight="100%">
                    { loading ? <div style={{position: "absolute", right: "25vw" , top: "50vh"}}><Spinner size="large" /></div>:
                    <VerticalStack gap={"5"}>
                        <Box padding={"4"} paddingBlockEnd={"0"} >
                            <HorizontalStack align="space-between">
                                {titleComp ? titleComp : 
                                    <Text variant="headingMd">
                                        {title}
                                    </Text>
                                }
                                <Button icon={CancelMajor} onClick={() => { handleExit()}} plain></Button>
                            </HorizontalStack>
                        </Box>
                        <Scrollable style={{ height: "92vh" }} shadow>
                        <Box paddingBlockEnd={"20"}>
                        <VerticalStack>
                        {
                            show ?
                                components.map((component, index) => {
                                    return (
                                        <Box key={index}>                                        
                                            {newComp ? <Box>
                                                {component}
                                            </Box>:
                                            <Box paddingInlineEnd={"4"} paddingInlineStart={"4"}>
                                                {component}
                                            </Box>
                                            }
                                            {(showDivider && index !== components.length - 1) ? <Divider /> : null}
                                        </Box>
                                    )
                                })
                                :null
                        }
                        </VerticalStack>
                        </Box>
                        </Scrollable>
                    </VerticalStack>
                    }
                </Box>      
            </div>
        </div>
    )
}

export default FlyLayout