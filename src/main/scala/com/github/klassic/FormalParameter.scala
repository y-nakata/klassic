package com.github.klassic

import com.github.klassic.TypeDescription.DynamicType
case class FormalParameter(name: String, description: TypeDescription = DynamicType)